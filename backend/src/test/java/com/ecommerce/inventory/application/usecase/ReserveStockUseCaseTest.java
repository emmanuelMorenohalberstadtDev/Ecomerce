package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.application.usecase.ReserveStockUseCase.ReserveStockCommand;
import com.ecommerce.inventory.domain.event.StockReservedEvent;
import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReserveStockUseCase}.
 *
 * <p>Mocks: {@link StockItemRepository}, {@link StockReservationRepository},
 * {@link StockMovementRepository}, {@link ApplicationEventPublisher}. No Spring context.
 *
 * <p>Case TC2 (shortage/all-or-nothing) can only prove, at the unit level, that no
 * reservation/movement/event side effect is persisted once a line comes back short — the actual
 * undo of the earlier-in-the-loop successful decrement is the {@code @Transactional} rollback on
 * the use case class itself (production code, not re-verified here); see
 * {@link #shouldNotPersistReservationMovementsOrEvent_whenShortageOccursMidBatch_TC2()}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReserveStockUseCaseTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private ReserveStockUseCase useCase;

    private final CheckoutSessionId checkoutSessionId = CheckoutSessionId.generate();
    private final ProductId productA = ProductId.generate();
    private final ProductId productB = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new ReserveStockUseCase(stockItemRepository, stockReservationRepository,
                stockMovementRepository, eventPublisher, clock);
    }

    // ── successful reservation (TC1) ──────────────────────────────────────────

    @Test
    void shouldCreateHeldReservation_whenAllLinesHaveSufficientStock_TC1() {
        when(stockItemRepository.decrementIfSufficientStock(productA, 2)).thenReturn(true);
        when(stockItemRepository.decrementIfSufficientStock(productB, 1)).thenReturn(true);
        when(stockReservationRepository.create(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = useCase.execute(new ReserveStockCommand(checkoutSessionId, List.of(
                new ReservationLineInput(productA, Quantity.of(2)),
                new ReservationLineInput(productB, Quantity.of(1))), null));

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(result.getCheckoutSessionId()).isEqualTo(checkoutSessionId);
        assertThat(result.getLines()).hasSize(2);
    }

    @Test
    void shouldWriteReserveMovementRow_perLine_TC1() {
        when(stockItemRepository.decrementIfSufficientStock(productA, 2)).thenReturn(true);
        when(stockItemRepository.decrementIfSufficientStock(productB, 1)).thenReturn(true);
        when(stockReservationRepository.create(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new ReserveStockCommand(checkoutSessionId, List.of(
                new ReservationLineInput(productA, Quantity.of(2)),
                new ReservationLineInput(productB, Quantity.of(1))), null));

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).record(captor.capture());
        List<StockMovement> movements = captor.getAllValues();
        assertThat(movements).allMatch(m -> m.movementType() == MovementType.RESERVE);
        assertThat(movements).extracting(StockMovement::quantityDelta).containsExactlyInAnyOrder(-2, -1);
        assertThat(movements).allMatch(m -> m.occurredAt().equals(clock.instant()));
    }

    @Test
    void shouldPublishStockReservedEvent_afterReservationIsPersisted_TC1() {
        when(stockItemRepository.decrementIfSufficientStock(productA, 2)).thenReturn(true);
        when(stockReservationRepository.create(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = useCase.execute(new ReserveStockCommand(checkoutSessionId, List.of(
                new ReservationLineInput(productA, Quantity.of(2))), null));

        ArgumentCaptor<StockReservedEvent> captor = ArgumentCaptor.forClass(StockReservedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        StockReservedEvent event = captor.getValue();
        assertThat(event.reservationId()).isEqualTo(result.getId());
        assertThat(event.checkoutSessionId()).isEqualTo(checkoutSessionId);
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
    }

    // ── shortage / all-or-nothing (TC2) ───────────────────────────────────────

    @Test
    void shouldThrowInsufficientStockException_whenAnyLineDecrementFails_TC2() {
        when(stockItemRepository.decrementIfSufficientStock(productA, 2)).thenReturn(true);
        when(stockItemRepository.decrementIfSufficientStock(productB, 5)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new ReserveStockCommand(checkoutSessionId, List.of(
                new ReservationLineInput(productA, Quantity.of(2)),
                new ReservationLineInput(productB, Quantity.of(5))), null)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldNotPersistReservationMovementsOrEvent_whenShortageOccursMidBatch_TC2() {
        when(stockItemRepository.decrementIfSufficientStock(productA, 2)).thenReturn(true);
        when(stockItemRepository.decrementIfSufficientStock(productB, 5)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new ReserveStockCommand(checkoutSessionId, List.of(
                new ReservationLineInput(productA, Quantity.of(2)),
                new ReservationLineInput(productB, Quantity.of(5))), null)))
                .isInstanceOf(InsufficientStockException.class);

        // Both decrements were attempted, in order — productA's succeeded before productB's
        // shortage aborted the loop. The @Transactional rollback on ReserveStockUseCase.execute
        // (production code) is what actually undoes productA's decrement; at the mock level the
        // testable contract is that no reservation, no movement rows, and no event ever reach the
        // collaborators once the batch aborts.
        verify(stockItemRepository).decrementIfSufficientStock(productA, 2);
        verify(stockItemRepository).decrementIfSufficientStock(productB, 5);
        verify(stockReservationRepository, never()).create(any());
        verify(stockMovementRepository, never()).record(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── validation ─────────────────────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenCommandHasNoLines() {
        assertThatThrownBy(() -> useCase.execute(new ReserveStockCommand(checkoutSessionId, List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(stockItemRepository, stockMovementRepository, eventPublisher);
        verify(stockReservationRepository, never()).create(any());
    }
}

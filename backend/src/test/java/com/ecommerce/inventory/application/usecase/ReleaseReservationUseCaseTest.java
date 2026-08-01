package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.event.StockReleasedEvent;
import com.ecommerce.inventory.domain.exception.InvalidReservationStateException;
import com.ecommerce.inventory.domain.exception.ReservationNotFoundException;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseReservationUseCase}.
 *
 * <p>Mocks: {@link StockReservationRepository}, {@link StockItemRepository},
 * {@link StockMovementRepository}, {@link ApplicationEventPublisher}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReleaseReservationUseCaseTest {

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private ReleaseReservationUseCase useCase;

    private final ReservationId reservationId = ReservationId.generate();
    private final CheckoutSessionId checkoutSessionId = CheckoutSessionId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new ReleaseReservationUseCase(stockReservationRepository, stockItemRepository,
                stockMovementRepository, eventPublisher, clock);
    }

    private StockReservation reservationWithStatus(ReservationStatus status, ReservedLine... lines) {
        return StockReservation.reconstitute(reservationId, checkoutSessionId, status,
                Expiry.none(), Instant.parse("2026-07-31T09:00:00Z"), List.of(lines));
    }

    // ── release / restock (TC4) ───────────────────────────────────────────────

    @Test
    void shouldTransitionHeldToReleased_TC4() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.HELD, new ReservedLine(productId, Quantity.of(2)))));

        StockReservation result = useCase.execute(reservationId);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(stockReservationRepository).updateStatus(reservationId, ReservationStatus.RELEASED);
    }

    @Test
    void shouldRestockEachLine_TC4() {
        ProductId productB = ProductId.generate();
        when(stockReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservationWithStatus(
                ReservationStatus.HELD,
                new ReservedLine(productId, Quantity.of(2)),
                new ReservedLine(productB, Quantity.of(5)))));

        useCase.execute(reservationId);

        verify(stockItemRepository).incrementAvailable(productId, 2);
        verify(stockItemRepository).incrementAvailable(productB, 5);
    }

    @Test
    void shouldWriteReleaseMovementRow_withPositiveQuantityDelta_TC4() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.HELD, new ReservedLine(productId, Quantity.of(2)))));

        useCase.execute(reservationId);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).record(captor.capture());
        StockMovement movement = captor.getValue();
        assertThat(movement.movementType()).isEqualTo(MovementType.RELEASE);
        assertThat(movement.quantityDelta()).isEqualTo(2);
        assertThat(movement.reservationId()).isEqualTo(reservationId);
        assertThat(movement.occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldPublishStockReleasedEvent_afterRestockAndLogging_TC4() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.HELD, new ReservedLine(productId, Quantity.of(2)))));

        StockReservation result = useCase.execute(reservationId);

        ArgumentCaptor<StockReleasedEvent> captor = ArgumentCaptor.forClass(StockReleasedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        StockReleasedEvent event = captor.getValue();
        assertThat(event.reservationId()).isEqualTo(result.getId());
        assertThat(event.checkoutSessionId()).isEqualTo(checkoutSessionId);
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
    }

    // ── not found ──────────────────────────────────────────────────────────

    @Test
    void shouldThrowReservationNotFoundException_whenReservationDoesNotExist() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(reservationId))
                .isInstanceOf(ReservationNotFoundException.class);

        verify(stockItemRepository, never()).incrementAvailable(any(), anyInt());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── invalid state transition (TC7) ────────────────────────────────────────

    @Test
    void shouldThrowInvalidReservationStateException_whenReservationNotHeld_TC7() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.RELEASED, new ReservedLine(productId, Quantity.of(2)))));

        assertThatThrownBy(() -> useCase.execute(reservationId))
                .isInstanceOf(InvalidReservationStateException.class);

        verify(stockReservationRepository, never()).updateStatus(any(), any());
        verify(stockItemRepository, never()).incrementAvailable(any(), anyInt());
        verify(stockMovementRepository, never()).record(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.exception.InvalidReservationStateException;
import com.ecommerce.inventory.domain.exception.ReservationNotFoundException;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.model.StockReservation;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommitReservationUseCase}.
 *
 * <p>Mocks: {@link StockReservationRepository}, {@link StockMovementRepository}. No Spring
 * context. Note there is no {@code StockItemRepository} collaborator here at all — commit is
 * status-only and never touches {@code quantity_available} (the decrement already happened at
 * reserve time), which this test class's constructor signature itself reflects.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CommitReservationUseCaseTest {

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private CommitReservationUseCase useCase;

    private final ReservationId reservationId = ReservationId.generate();
    private final CheckoutSessionId checkoutSessionId = CheckoutSessionId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new CommitReservationUseCase(stockReservationRepository, stockMovementRepository, clock);
    }

    private StockReservation reservationWithStatus(ReservationStatus status, ReservedLine... lines) {
        return StockReservation.reconstitute(reservationId, checkoutSessionId, status,
                Expiry.none(), Instant.parse("2026-07-31T09:00:00Z"), List.of(lines));
    }

    // ── commit: HELD -> COMMITTED, status-only (TC3) ──────────────────────────

    @Test
    void shouldTransitionHeldToCommitted_TC3() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.HELD, new ReservedLine(productId, Quantity.of(3)))));

        StockReservation result = useCase.execute(reservationId);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        verify(stockReservationRepository).updateStatus(reservationId, ReservationStatus.COMMITTED);
    }

    @Test
    void shouldWriteCommitMovementRow_withZeroQuantityDelta_TC3() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.HELD, new ReservedLine(productId, Quantity.of(3)))));

        useCase.execute(reservationId);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).record(captor.capture());
        StockMovement movement = captor.getValue();
        assertThat(movement.movementType()).isEqualTo(MovementType.COMMIT);
        assertThat(movement.quantityDelta()).isZero();
        assertThat(movement.productId()).isEqualTo(productId);
        assertThat(movement.reservationId()).isEqualTo(reservationId);
        assertThat(movement.occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldWriteOneCommitMovementRow_perLine_TC3() {
        ProductId productB = ProductId.generate();
        when(stockReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservationWithStatus(
                ReservationStatus.HELD,
                new ReservedLine(productId, Quantity.of(3)),
                new ReservedLine(productB, Quantity.of(1)))));

        useCase.execute(reservationId);

        verify(stockMovementRepository, times(2)).record(any(StockMovement.class));
    }

    // ── not found ──────────────────────────────────────────────────────────

    @Test
    void shouldThrowReservationNotFoundException_whenReservationDoesNotExist() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(reservationId))
                .isInstanceOf(ReservationNotFoundException.class);

        verify(stockReservationRepository, never()).updateStatus(any(), any());
        verify(stockMovementRepository, never()).record(any());
    }

    // ── invalid state transition (TC7) ────────────────────────────────────────

    @Test
    void shouldThrowInvalidReservationStateException_whenReservationNotHeld_TC7() {
        when(stockReservationRepository.findById(reservationId)).thenReturn(
                Optional.of(reservationWithStatus(ReservationStatus.COMMITTED, new ReservedLine(productId, Quantity.of(3)))));

        assertThatThrownBy(() -> useCase.execute(reservationId))
                .isInstanceOf(InvalidReservationStateException.class);

        verify(stockReservationRepository, never()).updateStatus(any(), any());
        verify(stockMovementRepository, never()).record(any());
    }
}

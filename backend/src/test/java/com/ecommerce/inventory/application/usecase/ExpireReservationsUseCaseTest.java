package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.exception.InvalidReservationStateException;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpireReservationsUseCase}.
 *
 * <p>Mocks: {@link StockReservationRepository}, {@link ReleaseReservationUseCase} (this use case
 * delegates the actual release/restock work per reservation, so the release side effects
 * themselves are covered by {@link ReleaseReservationUseCaseTest}, not re-verified here). No
 * Spring context.
 *
 * <p>"Reservations not yet expired or not HELD are untouched" (TC5) is proven by construction:
 * {@link ExpireReservationsUseCase#execute()} iterates exactly what
 * {@link StockReservationRepository#findHeldExpiredAsOf} returns and nothing else — there is no
 * additional in-memory filtering in the use case to separately unit-test. {@link
 * #shouldReturnEmptyList_whenNoReservationsExpired()} demonstrates that an empty repository result
 * yields zero releases (nothing is fabricated), and {@link
 * #shouldReleaseEveryHeldExpiredReservation_TC5()} demonstrates that only what the repository
 * returns gets released.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ExpireReservationsUseCaseTest {

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private ReleaseReservationUseCase releaseReservationUseCase;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private ExpireReservationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExpireReservationsUseCase(stockReservationRepository, releaseReservationUseCase, clock);
    }

    private StockReservation heldReservation(ReservationId id, Instant expiresAt) {
        return StockReservation.reconstitute(id, CheckoutSessionId.generate(), ReservationStatus.HELD,
                Expiry.at(expiresAt), expiresAt.minusSeconds(900),
                List.of(new ReservedLine(ProductId.generate(), Quantity.of(1))));
    }

    private StockReservation released(StockReservation held) {
        return StockReservation.reconstitute(held.getId(), held.getCheckoutSessionId(), ReservationStatus.RELEASED,
                held.getExpiresAt(), held.getCreatedAt(), held.getLines());
    }

    // ── expire sweep (TC5) ────────────────────────────────────────────────────

    @Test
    void shouldReleaseEveryHeldExpiredReservation_TC5() {
        StockReservation first = heldReservation(ReservationId.generate(), Instant.parse("2026-07-31T09:00:00Z"));
        StockReservation second = heldReservation(ReservationId.generate(), Instant.parse("2026-07-31T09:30:00Z"));
        when(stockReservationRepository.findHeldExpiredAsOf(clock.instant())).thenReturn(List.of(first, second));
        StockReservation firstReleased = released(first);
        StockReservation secondReleased = released(second);
        when(releaseReservationUseCase.execute(first.getId())).thenReturn(firstReleased);
        when(releaseReservationUseCase.execute(second.getId())).thenReturn(secondReleased);

        List<StockReservation> result = useCase.execute();

        assertThat(result).containsExactly(firstReleased, secondReleased);
        verify(releaseReservationUseCase).execute(first.getId());
        verify(releaseReservationUseCase).execute(second.getId());
    }

    @Test
    void shouldSkipReservation_whenConcurrentlyAlreadyTransitionedAwayFromHeld_TC5() {
        StockReservation first = heldReservation(ReservationId.generate(), Instant.parse("2026-07-31T09:00:00Z"));
        StockReservation second = heldReservation(ReservationId.generate(), Instant.parse("2026-07-31T09:30:00Z"));
        when(stockReservationRepository.findHeldExpiredAsOf(clock.instant())).thenReturn(List.of(first, second));
        when(releaseReservationUseCase.execute(first.getId()))
                .thenThrow(new InvalidReservationStateException("already committed by a concurrent payment"));
        StockReservation secondReleased = released(second);
        when(releaseReservationUseCase.execute(second.getId())).thenReturn(secondReleased);

        List<StockReservation> result = useCase.execute();

        assertThat(result).containsExactly(secondReleased);
    }

    @Test
    void shouldReturnEmptyList_whenNoReservationsExpired() {
        when(stockReservationRepository.findHeldExpiredAsOf(clock.instant())).thenReturn(List.of());

        List<StockReservation> result = useCase.execute();

        assertThat(result).isEmpty();
        verifyNoInteractions(releaseReservationUseCase);
    }
}

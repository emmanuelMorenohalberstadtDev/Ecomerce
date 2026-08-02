package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.model.SessionStatus;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpirePaymentWindowUseCase}.
 *
 * <p>Mocks: {@link CheckoutSessionRepository}, {@link ExpireCheckoutSessionUseCase} (this use case
 * delegates the actual release/expire/persist/publish work per session, so those side effects are
 * covered by {@link ExpireCheckoutSessionUseCaseTest}, not re-verified here). No Spring context.
 * Mirrors {@code inventory.application.usecase.ExpireReservationsUseCaseTest} exactly — same sweep
 * shape (find candidates, delegate per-item, tolerate one narrow race exception without aborting
 * the batch).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ExpirePaymentWindowUseCaseTest {

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private ExpireCheckoutSessionUseCase expireCheckoutSessionUseCase;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private ExpirePaymentWindowUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExpirePaymentWindowUseCase(checkoutSessionRepository, expireCheckoutSessionUseCase, clock);
    }

    private static RecalculatedTotal totalOf(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        return new RecalculatedTotal(List.of(), money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private CheckoutSession awaitingPaymentSession(CheckoutSessionId id, Instant deadline) {
        CheckoutSession session = CheckoutSession.start(id, CustomerId.generate(), CartId.generate(),
                totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "key-" + id, deadline.minusSeconds(900));
        session.moveToAwaitingPayment(ReservationId.generate(), deadline);
        return session;
    }

    private CheckoutSession expired(CheckoutSession source) {
        return CheckoutSession.reconstitute(source.getId(), source.getCustomerId(), source.getCartId(),
                SessionStatus.EXPIRED, source.getReservationId(),
                source.getRecalculatedTotal(), source.getExpectedTotal(), source.getPaymentDeadline(),
                source.getIdempotencyKey(), source.getVersion() + 1, source.getCreatedAt(), clock.instant());
    }

    // ── sweep releases every expired session ──────────────────────────────

    @Test
    void shouldExpireEveryAwaitingPaymentSessionPastDeadline() {
        CheckoutSession first = awaitingPaymentSession(CheckoutSessionId.generate(), Instant.parse("2026-07-31T09:00:00Z"));
        CheckoutSession second = awaitingPaymentSession(CheckoutSessionId.generate(), Instant.parse("2026-07-31T09:30:00Z"));
        when(checkoutSessionRepository.findAwaitingPaymentExpiredAsOf(clock.instant()))
                .thenReturn(List.of(first, second));
        CheckoutSession firstExpired = expired(first);
        CheckoutSession secondExpired = expired(second);
        when(expireCheckoutSessionUseCase.execute(first)).thenReturn(firstExpired);
        when(expireCheckoutSessionUseCase.execute(second)).thenReturn(secondExpired);

        List<CheckoutSession> result = useCase.execute();

        assertThat(result).containsExactly(firstExpired, secondExpired);
        verify(expireCheckoutSessionUseCase).execute(first);
        verify(expireCheckoutSessionUseCase).execute(second);
    }

    // ── benign race: one session already moved on, sweep continues ────────

    @Test
    void shouldSkipSession_andContinueTheBatch_whenConcurrentRaceThrowsInvalidCheckoutSessionStateException() {
        CheckoutSession first = awaitingPaymentSession(CheckoutSessionId.generate(), Instant.parse("2026-07-31T09:00:00Z"));
        CheckoutSession second = awaitingPaymentSession(CheckoutSessionId.generate(), Instant.parse("2026-07-31T09:30:00Z"));
        when(checkoutSessionRepository.findAwaitingPaymentExpiredAsOf(clock.instant()))
                .thenReturn(List.of(first, second));
        when(expireCheckoutSessionUseCase.execute(first))
                .thenThrow(new InvalidCheckoutSessionStateException("already committed/expired by a concurrent flow"));
        CheckoutSession secondExpired = expired(second);
        when(expireCheckoutSessionUseCase.execute(second)).thenReturn(secondExpired);

        List<CheckoutSession> result = useCase.execute();

        assertThat(result).containsExactly(secondExpired);
    }

    @Test
    void shouldReturnEmptyList_whenNoSessionsExpired() {
        when(checkoutSessionRepository.findAwaitingPaymentExpiredAsOf(clock.instant())).thenReturn(List.of());

        List<CheckoutSession> result = useCase.execute();

        assertThat(result).isEmpty();
        verifyNoInteractions(expireCheckoutSessionUseCase);
    }
}

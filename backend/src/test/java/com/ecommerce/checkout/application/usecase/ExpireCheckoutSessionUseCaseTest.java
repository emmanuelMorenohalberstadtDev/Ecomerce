package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutSessionExpiredEvent;
import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.model.SessionStatus;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpireCheckoutSessionUseCase}.
 *
 * <p>Mocks: {@link CheckoutSessionRepository}, {@link ReservationPort}, {@link ApplicationEventPublisher}.
 * No Spring context. Exercised standalone here even though it is only ever invoked as a proxied
 * bean call from {@link ExpirePaymentWindowUseCase}'s sweep — the release/expire/persist/publish
 * side effects live entirely in this class.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ExpireCheckoutSessionUseCaseTest {

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private ReservationPort reservationPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private ExpireCheckoutSessionUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();

    @BeforeEach
    void setUp() {
        useCase = new ExpireCheckoutSessionUseCase(checkoutSessionRepository, reservationPort, eventPublisher, clock);
    }

    private static RecalculatedTotal totalOf(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        return new RecalculatedTotal(List.of(), money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private CheckoutSession awaitingPaymentSession() {
        CheckoutSession session = CheckoutSession.start(CheckoutSessionId.generate(), customerId, CartId.generate(),
                totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "idem-key-1",
                Instant.parse("2026-07-31T09:45:00Z"));
        session.moveToAwaitingPayment(reservationId, Instant.parse("2026-07-31T10:00:00Z"));
        return session;
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void shouldReleaseReservation_beforeTransitioningSessionToExpired() {
        CheckoutSession session = awaitingPaymentSession();
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutSession result = useCase.execute(session);

        verify(reservationPort, times(1)).release(reservationId);
        assertThat(result.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void shouldPersistTheExpiredSession() {
        CheckoutSession session = awaitingPaymentSession();
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(session);

        ArgumentCaptor<CheckoutSession> captor = ArgumentCaptor.forClass(CheckoutSession.class);
        verify(checkoutSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void shouldPublishCheckoutSessionExpiredEvent_afterPersisting() {
        CheckoutSession session = awaitingPaymentSession();
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutSession result = useCase.execute(session);

        ArgumentCaptor<CheckoutSessionExpiredEvent> captor = ArgumentCaptor.forClass(CheckoutSessionExpiredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        CheckoutSessionExpiredEvent event = captor.getValue();
        assertThat(event.checkoutSessionId()).isEqualTo(result.getId());
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.reservationId()).isEqualTo(reservationId);
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
    }

    // ── invalid state (session not AWAITING_PAYMENT) ───────────────────────

    @Test
    void shouldThrowInvalidCheckoutSessionStateException_andNotPublishEvent_whenSessionIsNotAwaitingPayment() {
        CheckoutSession pending = CheckoutSession.start(CheckoutSessionId.generate(), customerId, CartId.generate(),
                totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "idem-key-1", clock.instant());

        assertThatThrownBy(() -> useCase.execute(pending))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);

        verify(checkoutSessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── reservation port propagation ────────────────────────────────────────

    @Test
    void shouldPropagateInvalidCheckoutSessionStateException_whenReservationPortRejectsRelease() {
        CheckoutSession session = awaitingPaymentSession();
        doThrow(new InvalidCheckoutSessionStateException("reservation already committed"))
                .when(reservationPort).release(reservationId);

        assertThatThrownBy(() -> useCase.execute(session))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);

        verify(checkoutSessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

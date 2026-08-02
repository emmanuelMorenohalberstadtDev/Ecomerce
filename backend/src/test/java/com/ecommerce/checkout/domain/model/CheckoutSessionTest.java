package com.ecommerce.checkout.domain.model;

import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CheckoutSession} aggregate root.
 *
 * <p>No Spring context — pure domain logic via factories and domain methods, mirroring
 * {@code cart.domain.model.CartTest}'s style for the sibling aggregate.
 */
@Tag("unit")
class CheckoutSessionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-07-31T12:15:00Z");

    private static RecalculatedTotal total(String grandTotal, String currency) {
        Money money = new Money(new BigDecimal(grandTotal), currency);
        return new RecalculatedTotal(List.of(), money, Money.zero(currency), Money.zero(currency), money);
    }

    private static CheckoutSession pendingSession(String recalculatedGrandTotal, String expectedTotal) {
        return CheckoutSession.start(CheckoutSessionId.generate(), CustomerId.generate(), CartId.generate(),
                total(recalculatedGrandTotal, "USD"), new Money(new BigDecimal(expectedTotal), "USD"),
                "idem-key-1", NOW);
    }

    private static CheckoutSession awaitingPaymentSession() {
        CheckoutSession session = pendingSession("50.00", "50.00");
        session.moveToAwaitingPayment(ReservationId.generate(), DEADLINE);
        return session;
    }

    // ── start ──────────────────────────────────────────────────────────────

    @Test
    void start_producesPendingConfirmationStatus_withNoReservation() {
        CheckoutSession session = pendingSession("50.00", "50.00");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.PENDING_CONFIRMATION);
        assertThat(session.getReservationId()).isNull();
        assertThat(session.getPaymentDeadline()).isNull();
    }

    @Test
    void start_setsVersionToZeroAndTimestampsToNow() {
        CheckoutSession session = pendingSession("50.00", "50.00");

        assertThat(session.getVersion()).isZero();
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void start_storesIdempotencyKeyAndExpectedTotal() {
        CheckoutSession session = pendingSession("50.00", "45.00");

        assertThat(session.getIdempotencyKey()).isEqualTo("idem-key-1");
        assertThat(session.getExpectedTotal()).isEqualTo(new Money(new BigDecimal("45.00"), "USD"));
    }

    // ── requiresReconfirmation ─────────────────────────────────────────────

    @Test
    void requiresReconfirmation_returnsFalse_whenExpectedTotalMatchesRecalculatedGrandTotal() {
        CheckoutSession session = pendingSession("50.00", "50.00");

        assertThat(session.requiresReconfirmation()).isFalse();
    }

    @Test
    void requiresReconfirmation_returnsTrue_whenExpectedTotalDiffersFromRecalculatedGrandTotal() {
        CheckoutSession session = pendingSession("55.00", "50.00");

        assertThat(session.requiresReconfirmation()).isTrue();
    }

    @Test
    void requiresReconfirmation_returnsTrue_whenCurrenciesDiffer_withoutThrowing() {
        RecalculatedTotal eurTotal = total("50.00", "EUR");
        CheckoutSession session = CheckoutSession.start(CheckoutSessionId.generate(), CustomerId.generate(),
                CartId.generate(), eurTotal, new Money(new BigDecimal("50.00"), "USD"), "idem-key-1", NOW);

        // Money#equals compares currency too, unlike the arithmetic helpers (isGreaterThan/add),
        // which throw on a currency mismatch — requiresReconfirmation() must not blow up here,
        // it must simply treat differing currencies as "not equal", i.e. still needs reconfirmation.
        assertThat(session.requiresReconfirmation()).isTrue();
    }

    // ── moveToAwaitingPayment ──────────────────────────────────────────────

    @Test
    void moveToAwaitingPayment_transitionsFromPendingConfirmation_settingReservationAndDeadline() {
        CheckoutSession session = pendingSession("50.00", "50.00");
        ReservationId reservationId = ReservationId.generate();

        session.moveToAwaitingPayment(reservationId, DEADLINE);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.AWAITING_PAYMENT);
        assertThat(session.getReservationId()).isEqualTo(reservationId);
        assertThat(session.getPaymentDeadline()).isEqualTo(DEADLINE);
    }

    @Test
    void moveToAwaitingPayment_throwsInvalidCheckoutSessionStateException_whenAlreadyAwaitingPayment() {
        CheckoutSession session = awaitingPaymentSession();

        assertThatThrownBy(() -> session.moveToAwaitingPayment(ReservationId.generate(), DEADLINE))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);
    }

    @Test
    void moveToAwaitingPayment_throwsInvalidCheckoutSessionStateException_whenExpired() {
        CheckoutSession session = awaitingPaymentSession();
        session.expire();

        assertThatThrownBy(() -> session.moveToAwaitingPayment(ReservationId.generate(), DEADLINE))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);
    }

    @Test
    void moveToAwaitingPayment_throwsNullPointerException_whenReservationIdIsNull() {
        CheckoutSession session = pendingSession("50.00", "50.00");

        assertThatThrownBy(() -> session.moveToAwaitingPayment(null, DEADLINE))
                .isInstanceOf(NullPointerException.class);
    }

    // ── expire ─────────────────────────────────────────────────────────────

    @Test
    void expire_transitionsFromAwaitingPaymentToExpired() {
        CheckoutSession session = awaitingPaymentSession();

        session.expire();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void expire_throwsInvalidCheckoutSessionStateException_whenStillPendingConfirmation() {
        CheckoutSession session = pendingSession("50.00", "50.00");

        assertThatThrownBy(session::expire)
                .isInstanceOf(InvalidCheckoutSessionStateException.class);
    }

    @Test
    void expire_throwsInvalidCheckoutSessionStateException_whenAlreadyExpired() {
        CheckoutSession session = awaitingPaymentSession();
        session.expire();

        assertThatThrownBy(session::expire)
                .isInstanceOf(InvalidCheckoutSessionStateException.class);
    }

    // ── refreshRecalculatedTotal ───────────────────────────────────────────

    @Test
    void refreshRecalculatedTotal_replacesTotal_whenPendingConfirmation() {
        CheckoutSession session = pendingSession("50.00", "50.00");
        RecalculatedTotal fresh = total("60.00", "USD");

        session.refreshRecalculatedTotal(fresh);

        assertThat(session.getRecalculatedTotal()).isEqualTo(fresh);
    }

    @Test
    void refreshRecalculatedTotal_throwsInvalidCheckoutSessionStateException_whenNotPendingConfirmation() {
        CheckoutSession session = awaitingPaymentSession();

        assertThatThrownBy(() -> session.refreshRecalculatedTotal(total("60.00", "USD")))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);
    }

    @Test
    void refreshRecalculatedTotal_throwsNullPointerException_whenNewTotalIsNull() {
        CheckoutSession session = pendingSession("50.00", "50.00");

        assertThatThrownBy(() -> session.refreshRecalculatedTotal(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── reconstitute ───────────────────────────────────────────────────────

    @Test
    void reconstitute_restoresExactState() {
        CheckoutSessionId id = CheckoutSessionId.generate();
        CustomerId customerId = CustomerId.generate();
        CartId cartId = CartId.generate();
        ReservationId reservationId = ReservationId.generate();
        RecalculatedTotal recalculatedTotal = total("50.00", "USD");
        Money expectedTotal = new Money(new BigDecimal("50.00"), "USD");
        Instant updatedAt = NOW.plusSeconds(60);

        CheckoutSession session = CheckoutSession.reconstitute(id, customerId, cartId,
                SessionStatus.AWAITING_PAYMENT, reservationId, recalculatedTotal, expectedTotal, DEADLINE,
                "idem-key-1", 3L, NOW, updatedAt);

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getCustomerId()).isEqualTo(customerId);
        assertThat(session.getCartId()).isEqualTo(cartId);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.AWAITING_PAYMENT);
        assertThat(session.getReservationId()).isEqualTo(reservationId);
        assertThat(session.getRecalculatedTotal()).isEqualTo(recalculatedTotal);
        assertThat(session.getExpectedTotal()).isEqualTo(expectedTotal);
        assertThat(session.getPaymentDeadline()).isEqualTo(DEADLINE);
        assertThat(session.getIdempotencyKey()).isEqualTo("idem-key-1");
        assertThat(session.getVersion()).isEqualTo(3L);
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenIdIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(null, CustomerId.generate(), CartId.generate(),
                SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenCustomerIdIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), null, CartId.generate(),
                SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenCartIdIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), CustomerId.generate(),
                null, SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenStatusIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), CustomerId.generate(),
                CartId.generate(), null, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenRecalculatedTotalIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), CustomerId.generate(),
                CartId.generate(), SessionStatus.PENDING_CONFIRMATION, null, null,
                new Money(new BigDecimal("50.00"), "USD"), null, "key", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenExpectedTotalIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), CustomerId.generate(),
                CartId.generate(), SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                null, null, "key", 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenVersionIsNegative() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), CustomerId.generate(),
                CartId.generate(), SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key", -1L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenCreatedAtIsNull() {
        assertThatThrownBy(() -> CheckoutSession.reconstitute(CheckoutSessionId.generate(), CustomerId.generate(),
                CartId.generate(), SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key", 0L, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── equals/hashCode ────────────────────────────────────────────────────

    @Test
    void equals_isBasedOnId() {
        CheckoutSessionId id = CheckoutSessionId.generate();
        CheckoutSession a = CheckoutSession.reconstitute(id, CustomerId.generate(), CartId.generate(),
                SessionStatus.PENDING_CONFIRMATION, null, total("50.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), null, "key-a", 0L, NOW, NOW);
        CheckoutSession b = CheckoutSession.reconstitute(id, CustomerId.generate(), CartId.generate(),
                SessionStatus.AWAITING_PAYMENT, ReservationId.generate(), total("99.00", "USD"),
                new Money(new BigDecimal("50.00"), "USD"), DEADLINE, "key-b", 4L, NOW, NOW);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void equals_returnsFalse_whenIdsDiffer() {
        CheckoutSession a = pendingSession("50.00", "50.00");
        CheckoutSession b = pendingSession("50.00", "50.00");

        assertThat(a).isNotEqualTo(b);
    }
}

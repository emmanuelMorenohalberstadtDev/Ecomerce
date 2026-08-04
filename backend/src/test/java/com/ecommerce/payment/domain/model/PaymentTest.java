package com.ecommerce.payment.domain.model;

import com.ecommerce.payment.domain.exception.InvalidPaymentStateException;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Payment} aggregate root — the payment status state machine
 * ({@code PENDING -> CAPTURED -> REFUNDED}), attempt/refund recording, and reconstitution. No
 * Spring context, mirroring {@code order.domain.model.OrderTest}'s style for the sibling
 * aggregate.
 */
@Tag("unit")
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-02T12:30:00Z");

    private final OrderId orderId = OrderId.generate();
    private final CustomerId customerId = CustomerId.generate();
    private final Money amount = new Money(new BigDecimal("25.00"), "USD");

    private Payment pendingPayment() {
        return Payment.initiate(PaymentId.generate(), orderId, customerId, amount, NOW);
    }

    private Payment capturedPayment() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", NOW);
        payment.markCaptured(LATER);
        return payment;
    }

    private Payment refundedPayment() {
        Payment payment = capturedPayment();
        payment.markRefunded(RefundReason.ORDER_CANCELLED, null, LATER);
        return payment;
    }

    // ── initiate() factory ────────────────────────────────────────────────

    @Test
    void initiate_producesPendingStatus_withZeroVersionAndEmptyAttemptsAndRefunds() {
        Payment payment = pendingPayment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getVersion()).isZero();
        assertThat(payment.getAttempts()).isEmpty();
        assertThat(payment.getRefunds()).isEmpty();
    }

    @Test
    void initiate_storesOrderIdCustomerIdAndAmount() {
        Payment payment = pendingPayment();

        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getCustomerId()).isEqualTo(customerId);
        assertThat(payment.getAmount()).isEqualTo(amount);
    }

    @Test
    void initiate_setsCreatedAtAndUpdatedAtToNow() {
        Payment payment = pendingPayment();

        assertThat(payment.getCreatedAt()).isEqualTo(NOW);
        assertThat(payment.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void initiate_throwsIllegalArgumentException_whenIdIsNull() {
        assertThatThrownBy(() -> Payment.initiate(null, orderId, customerId, amount, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiate_throwsIllegalArgumentException_whenOrderIdIsNull() {
        assertThatThrownBy(() -> Payment.initiate(PaymentId.generate(), null, customerId, amount, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiate_throwsIllegalArgumentException_whenCustomerIdIsNull() {
        assertThatThrownBy(() -> Payment.initiate(PaymentId.generate(), orderId, null, amount, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiate_throwsIllegalArgumentException_whenAmountIsNull() {
        assertThatThrownBy(() -> Payment.initiate(PaymentId.generate(), orderId, customerId, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiate_throwsNullPointerException_whenNowIsNull() {
        assertThatThrownBy(() -> Payment.initiate(PaymentId.generate(), orderId, customerId, amount, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── recordAttempt() ────────────────────────────────────────────────────

    @Test
    void recordAttempt_appendsAttempt_andLeavesStatusPending() {
        Payment payment = pendingPayment();

        payment.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1", LATER);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAttempts()).hasSize(1);
        PaymentAttempt attempt = payment.getLatestAttempt();
        assertThat(attempt.idempotencyKey()).isEqualTo("key-1");
        assertThat(attempt.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(attempt.declineReason()).isEqualTo(DeclineReason.CARD_DECLINED);
        assertThat(attempt.gatewayReference()).isEqualTo("SIM-ref-1");
        assertThat(attempt.amount()).isEqualTo(amount);
        assertThat(attempt.attemptedAt()).isEqualTo(LATER);
    }

    @Test
    void recordAttempt_snapshotsTheAggregatesAmount_ontoTheAttempt() {
        Payment payment = pendingPayment();

        payment.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", LATER);

        assertThat(payment.getLatestAttempt().amount()).isEqualTo(amount);
    }

    @Test
    void recordAttempt_throwsInvalidPaymentStateException_whenAlreadyCaptured() {
        Payment payment = capturedPayment();

        assertThatThrownBy(() -> payment.recordAttempt("key-2", PaymentOutcome.APPROVED, null, "SIM-ref-2", LATER))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void recordAttempt_throwsInvalidPaymentStateException_whenAlreadyRefunded() {
        Payment payment = refundedPayment();

        assertThatThrownBy(() -> payment.recordAttempt("key-2", PaymentOutcome.APPROVED, null, "SIM-ref-2", LATER))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    // ── findAttemptByIdempotencyKey() — idempotent replay lookup ────────────

    @Test
    void findAttemptByIdempotencyKey_returnsTheMatchingAttempt_whenPresent() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1", NOW);

        Optional<PaymentAttempt> found = payment.findAttemptByIdempotencyKey("key-1");

        assertThat(found).isPresent();
        assertThat(found.get().idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void findAttemptByIdempotencyKey_returnsEmpty_whenNoAttemptHasThatKey() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1", NOW);

        assertThat(payment.findAttemptByIdempotencyKey("key-does-not-exist")).isEmpty();
    }

    @Test
    void findAttemptByIdempotencyKey_returnsEmpty_whenNoAttemptsRecordedYet() {
        Payment payment = pendingPayment();

        assertThat(payment.findAttemptByIdempotencyKey("key-1")).isEmpty();
    }

    // ── markCaptured() ─────────────────────────────────────────────────────

    @Test
    void markCaptured_transitionsFromPendingToCaptured_andUpdatesUpdatedAt() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", NOW);

        payment.markCaptured(LATER);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void markCaptured_throwsInvalidPaymentStateException_whenAlreadyCaptured() {
        Payment payment = capturedPayment();

        assertThatThrownBy(() -> payment.markCaptured(LATER))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void markCaptured_throwsInvalidPaymentStateException_whenAlreadyRefunded() {
        Payment payment = refundedPayment();

        assertThatThrownBy(() -> payment.markCaptured(LATER))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    // ── markRefunded() ─────────────────────────────────────────────────────

    @Test
    void markRefunded_transitionsFromCapturedToRefunded_andAppendsRefund() {
        Payment payment = capturedPayment();

        payment.markRefunded(RefundReason.ORDER_CANCELLED, "SIM-refund-ref", LATER);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getUpdatedAt()).isEqualTo(LATER);
        assertThat(payment.getRefunds()).hasSize(1);
        Refund refund = payment.getLatestRefund();
        assertThat(refund.amount()).isEqualTo(amount);
        assertThat(refund.reason()).isEqualTo(RefundReason.ORDER_CANCELLED);
        assertThat(refund.gatewayReference()).isEqualTo("SIM-refund-ref");
        assertThat(refund.issuedAt()).isEqualTo(LATER);
    }

    @Test
    void markRefunded_throwsInvalidPaymentStateException_whenStillPending() {
        Payment payment = pendingPayment();

        assertThatThrownBy(() -> payment.markRefunded(RefundReason.ORDER_CANCELLED, null, LATER))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void markRefunded_throwsInvalidPaymentStateException_whenAlreadyRefunded() {
        Payment payment = refundedPayment();

        assertThatThrownBy(() -> payment.markRefunded(RefundReason.ORDER_CANCELLED, null, LATER))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    // ── getLatestAttempt() / getLatestRefund() ─────────────────────────────

    @Test
    void getLatestAttempt_returnsTheMostRecentlyAppendedAttempt() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1", NOW);

        assertThat(payment.getLatestAttempt().idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void getLatestRefund_returnsTheMostRecentlyAppendedRefund() {
        Payment payment = capturedPayment();
        payment.markRefunded(RefundReason.ORDER_CANCELLED, "SIM-refund-ref", LATER);

        assertThat(payment.getLatestRefund().gatewayReference()).isEqualTo("SIM-refund-ref");
    }

    // ── getAttempts()/getRefunds() — unmodifiable, defensively-copied views ──

    @Test
    void getAttempts_returnsUnmodifiableView() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", NOW);

        List<PaymentAttempt> attempts = payment.getAttempts();

        assertThatThrownBy(() -> attempts.add(attempts.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAttempts_isUnaffectedByMutatingAPreviouslyReturnedView() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", NOW);

        List<PaymentAttempt> firstView = new ArrayList<>(payment.getAttempts());
        firstView.clear();

        assertThat(payment.getAttempts()).hasSize(1);
    }

    @Test
    void getRefunds_returnsUnmodifiableView() {
        Payment payment = refundedPayment();

        List<Refund> refunds = payment.getRefunds();

        assertThatThrownBy(() -> refunds.add(refunds.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getRefunds_isUnaffectedByMutatingAPreviouslyReturnedView() {
        Payment payment = refundedPayment();

        List<Refund> firstView = new ArrayList<>(payment.getRefunds());
        firstView.clear();

        assertThat(payment.getRefunds()).hasSize(1);
    }

    // ── reconstitute() ─────────────────────────────────────────────────────

    @Test
    void reconstitute_restoresExactState() {
        PaymentId id = PaymentId.generate();
        PaymentAttempt attempt = new PaymentAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", amount, NOW);
        Refund refund = new Refund(amount, RefundReason.ORDER_CANCELLED, "SIM-refund-ref", LATER);

        Payment payment = Payment.reconstitute(id, orderId, customerId, PaymentStatus.REFUNDED, amount, 3L, NOW,
                LATER, List.of(attempt), List.of(refund));

        assertThat(payment.getId()).isEqualTo(id);
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getCustomerId()).isEqualTo(customerId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getVersion()).isEqualTo(3L);
        assertThat(payment.getCreatedAt()).isEqualTo(NOW);
        assertThat(payment.getUpdatedAt()).isEqualTo(LATER);
        assertThat(payment.getAttempts()).containsExactly(attempt);
        assertThat(payment.getRefunds()).containsExactly(refund);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenVersionIsNegative() {
        assertThatThrownBy(() -> Payment.reconstitute(PaymentId.generate(), orderId, customerId,
                PaymentStatus.PENDING, amount, -1L, NOW, NOW, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenAttemptsIsNull() {
        assertThatThrownBy(() -> Payment.reconstitute(PaymentId.generate(), orderId, customerId,
                PaymentStatus.PENDING, amount, 0L, NOW, NOW, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenRefundsIsNull() {
        assertThatThrownBy(() -> Payment.reconstitute(PaymentId.generate(), orderId, customerId,
                PaymentStatus.PENDING, amount, 0L, NOW, NOW, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── equals/hashCode ────────────────────────────────────────────────────

    @Test
    void equals_isBasedOnId() {
        PaymentId id = PaymentId.generate();
        Payment a = Payment.reconstitute(id, orderId, customerId, PaymentStatus.PENDING, amount, 0L, NOW, NOW,
                List.of(), List.of());
        Payment b = Payment.reconstitute(id, OrderId.generate(), CustomerId.generate(), PaymentStatus.CAPTURED,
                new Money(new BigDecimal("99.00"), "USD"), 5L, NOW, LATER, List.of(), List.of());

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void equals_returnsFalse_whenIdsDiffer() {
        Payment a = pendingPayment();
        Payment b = pendingPayment();

        assertThat(a).isNotEqualTo(b);
    }
}

package com.ecommerce.payment.infrastructure.adapter;

import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort.ChargeRequest;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort.GatewayOutcome;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link SimulatedPaymentGatewayAdapter} — asserts the exact deterministic bucket
 * boundaries described in its own class javadoc: the outcome is derived from
 * {@code idempotencyKey.hashCode()}, bucketed 0-99 via {@code Math.floorMod(hash, 100)} — buckets
 * {@code [0, 90)} -> {@code APPROVED}, {@code [90, 95)} -> {@code TIMEOUT},
 * {@code [95, 100)} -> {@code DECLINED} (one of each of the 5 {@link DeclineReason} values, in enum
 * order, evenly across the last 5 buckets).
 *
 * <p>The idempotency keys below were chosen offline so that
 * {@code Math.floorMod(key.hashCode(), 100)} lands exactly on each boundary this adapter's own
 * javadoc documents — no flakiness, no reliance on the production formula inside the test itself
 * beyond the bucket <em>value</em>, which is asserted as a precondition on each fixture key so a
 * change to {@link String#hashCode()}'s algorithm (not expected, but not this test's business to
 * assume) would fail loudly here rather than silently.
 */
@Tag("unit")
class SimulatedPaymentGatewayAdapterTest {

    private final SimulatedPaymentGatewayAdapter adapter = new SimulatedPaymentGatewayAdapter();

    private static final OrderId ORDER_ID = OrderId.generate();
    private static final Money AMOUNT = new Money(new BigDecimal("25.00"), "USD");

    // Precomputed offline: Math.floorMod(key.hashCode(), 100) for each key below.
    private static final String BUCKET_0_KEY = "key-42";    // bucket 0  -> APPROVED (lower boundary)
    private static final String BUCKET_89_KEY = "key-171";  // bucket 89 -> APPROVED (upper boundary)
    private static final String BUCKET_90_KEY = "key-172";  // bucket 90 -> TIMEOUT (lower boundary)
    private static final String BUCKET_94_KEY = "key-73";   // bucket 94 -> TIMEOUT (upper boundary)
    private static final String BUCKET_95_KEY = "key-74";   // bucket 95 -> DECLINED / INSUFFICIENT_FUNDS
    private static final String BUCKET_96_KEY = "key-75";   // bucket 96 -> DECLINED / CARD_DECLINED
    private static final String BUCKET_97_KEY = "key-76";   // bucket 97 -> DECLINED / FRAUD_SUSPECTED
    private static final String BUCKET_98_KEY = "key-40";   // bucket 98 -> DECLINED / GATEWAY_TIMEOUT
    private static final String BUCKET_99_KEY = "key-41";   // bucket 99 -> DECLINED / GENERIC_DECLINE

    private static void assertBucket(String key, int expectedBucket) {
        assertThat(Math.floorMod(key.hashCode(), 100)).as("bucket for key '%s'", key).isEqualTo(expectedBucket);
    }

    private GatewayOutcome chargeWith(String idempotencyKey) {
        return adapter.charge(new ChargeRequest(ORDER_ID, AMOUNT, idempotencyKey));
    }

    // ── APPROVED bucket [0, 90) ──────────────────────────────────────────────

    @Test
    void charge_returnsApproved_atLowerBoundaryOfApprovedBucket() {
        assertBucket(BUCKET_0_KEY, 0);

        GatewayOutcome outcome = chargeWith(BUCKET_0_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.APPROVED);
        assertThat(outcome.declineReason()).isNull();
        assertThat(outcome.gatewayReference()).startsWith("SIM-");
    }

    @Test
    void charge_returnsApproved_atUpperBoundaryOfApprovedBucket() {
        assertBucket(BUCKET_89_KEY, 89);

        GatewayOutcome outcome = chargeWith(BUCKET_89_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.APPROVED);
    }

    // ── TIMEOUT bucket [90, 95) ──────────────────────────────────────────────

    @Test
    void charge_returnsTimeout_atLowerBoundaryOfTimeoutBucket() {
        assertBucket(BUCKET_90_KEY, 90);

        GatewayOutcome outcome = chargeWith(BUCKET_90_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.TIMEOUT);
        assertThat(outcome.declineReason()).isNull();
        assertThat(outcome.gatewayReference()).isNull();
    }

    @Test
    void charge_returnsTimeout_atUpperBoundaryOfTimeoutBucket() {
        assertBucket(BUCKET_94_KEY, 94);

        GatewayOutcome outcome = chargeWith(BUCKET_94_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.TIMEOUT);
    }

    // ── DECLINED bucket [95, 100) — one of each DeclineReason, evenly ────────

    @Test
    void charge_returnsDeclinedWithInsufficientFunds_atBucket95() {
        assertBucket(BUCKET_95_KEY, 95);

        GatewayOutcome outcome = chargeWith(BUCKET_95_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(outcome.declineReason()).isEqualTo(DeclineReason.INSUFFICIENT_FUNDS);
        assertThat(outcome.gatewayReference()).startsWith("SIM-");
    }

    @Test
    void charge_returnsDeclinedWithCardDeclined_atBucket96() {
        assertBucket(BUCKET_96_KEY, 96);

        GatewayOutcome outcome = chargeWith(BUCKET_96_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(outcome.declineReason()).isEqualTo(DeclineReason.CARD_DECLINED);
    }

    @Test
    void charge_returnsDeclinedWithFraudSuspected_atBucket97() {
        assertBucket(BUCKET_97_KEY, 97);

        GatewayOutcome outcome = chargeWith(BUCKET_97_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(outcome.declineReason()).isEqualTo(DeclineReason.FRAUD_SUSPECTED);
    }

    @Test
    void charge_returnsDeclinedWithGatewayTimeout_atBucket98() {
        assertBucket(BUCKET_98_KEY, 98);

        GatewayOutcome outcome = chargeWith(BUCKET_98_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(outcome.declineReason()).isEqualTo(DeclineReason.GATEWAY_TIMEOUT);
    }

    @Test
    void charge_returnsDeclinedWithGenericDecline_atBucket99() {
        assertBucket(BUCKET_99_KEY, 99);

        GatewayOutcome outcome = chargeWith(BUCKET_99_KEY);

        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(outcome.declineReason()).isEqualTo(DeclineReason.GENERIC_DECLINE);
    }

    // ── determinism ──────────────────────────────────────────────────────────

    @Test
    void charge_isDeterministic_forTheSameIdempotencyKey() {
        GatewayOutcome first = chargeWith(BUCKET_0_KEY);
        GatewayOutcome second = chargeWith(BUCKET_0_KEY);

        assertThat(first.outcome()).isEqualTo(second.outcome());
    }
}

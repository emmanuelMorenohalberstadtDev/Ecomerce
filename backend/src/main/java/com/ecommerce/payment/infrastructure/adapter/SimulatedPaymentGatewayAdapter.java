package com.ecommerce.payment.infrastructure.adapter;

import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort;
import com.ecommerce.shared.uuid.UuidV7Generator;

/**
 * Simulated payment-service-provider (PSP) adapter — v1's only {@link PaymentGatewayPort}
 * implementation (domain-model.md §1 payment: "non-goal: real PSP"). Never sees or stores PAN/CVV
 * (security-architecture §1.3) — only an amount, an order id, and an idempotency key go in; only an
 * outcome and an opaque reference come out.
 *
 * <p><strong>v1 simulation policy (this class's own judgment call, no product requirement dictates
 * the distribution):</strong> the outcome is derived deterministically from
 * {@code request.idempotencyKey()}'s hash, bucketed 0-99 — 90% {@code APPROVED}, 5%
 * {@code TIMEOUT}, 5% {@code DECLINED} (one of each of the 5 {@link DeclineReason} values, evenly).
 * Deterministic given the same key so this adapter's own unit test can assert exact bucket
 * boundaries without flakiness; test-engineer testing {@code SubmitPaymentUseCase} itself should
 * prefer a fake/mock {@link PaymentGatewayPort} over exercising this adapter's real distribution,
 * per the handoff notes. Not intended to resemble a real PSP's actual decline heuristics — a
 * non-goal.
 */
public class SimulatedPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final DeclineReason[] DECLINE_REASONS = DeclineReason.values();

    private static final int APPROVED_UPPER_BOUND = 90; // buckets [0, 90) -> APPROVED
    private static final int TIMEOUT_UPPER_BOUND = 95;  // buckets [90, 95) -> TIMEOUT, [95, 100) -> DECLINED

    @Override
    public GatewayOutcome charge(ChargeRequest request) {
        int bucket = Math.floorMod(request.idempotencyKey().hashCode(), 100);

        if (bucket < APPROVED_UPPER_BOUND) {
            return new GatewayOutcome(PaymentOutcome.APPROVED, null, simulatedReference());
        }
        if (bucket < TIMEOUT_UPPER_BOUND) {
            return new GatewayOutcome(PaymentOutcome.TIMEOUT, null, null);
        }
        DeclineReason reason = DECLINE_REASONS[(bucket - TIMEOUT_UPPER_BOUND) % DECLINE_REASONS.length];
        return new GatewayOutcome(PaymentOutcome.DECLINED, reason, simulatedReference());
    }

    private static String simulatedReference() {
        return "SIM-" + UuidV7Generator.generate();
    }
}

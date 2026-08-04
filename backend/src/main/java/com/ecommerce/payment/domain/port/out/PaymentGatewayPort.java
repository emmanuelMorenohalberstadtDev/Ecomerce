package com.ecommerce.payment.domain.port.out;

import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.money.Money;

import java.util.Objects;

/**
 * Payment's own outbound port to the payment-service-provider (PSP) — the simulated adapter is the
 * only implementation in v1 (domain-model.md §1 payment: "non-goal: real PSP"). Unlike
 * {@link OrderPort}, this is not a cross-bounded-context port — it is an outbound infrastructure
 * port entirely internal to the payment context (ADR-0003's own follow-up note: "follows this same
 * recipe ... no new pattern decision needed"), so it may reference payment's own domain types
 * ({@link PaymentOutcome}, {@link DeclineReason}) directly.
 *
 * <p><strong>No PAN/CVV ever crosses this boundary</strong> (security-architecture §1.3) — only an
 * amount, an order id for correlation, and the client-supplied idempotency key; the outcome carries
 * only an opaque gateway reference, never card-shaped data.
 *
 * <p>Implemented by {@code payment.infrastructure.adapter.SimulatedPaymentGatewayAdapter}.
 */
public interface PaymentGatewayPort {

    /**
     * Charges {@code request.amount()} against the (simulated) PSP. The amount must come
     * exclusively from {@code OrderPort.getForPayment(...).grandTotal()} — never a client-supplied
     * value (ADR-0005 sanity check); enforcing that is the caller's ({@code SubmitPaymentUseCase})
     * responsibility, not this port's.
     */
    GatewayOutcome charge(ChargeRequest request);

    record ChargeRequest(OrderId orderId, Money amount, String idempotencyKey) {

        public ChargeRequest {
            Objects.requireNonNull(orderId, "ChargeRequest orderId must not be null");
            Objects.requireNonNull(amount, "ChargeRequest amount must not be null");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("ChargeRequest idempotencyKey must not be null or blank");
            }
        }
    }

    /**
     * @param declineReason non-null if and only if {@code outcome == DECLINED} — mirrors
     *                       {@code PaymentAttempt}'s own cross-field invariant
     * @param gatewayReference the PSP's opaque reference; nullable (a {@code TIMEOUT} outcome may
     *                          carry none)
     */
    record GatewayOutcome(PaymentOutcome outcome, DeclineReason declineReason, String gatewayReference) {

        public GatewayOutcome {
            Objects.requireNonNull(outcome, "GatewayOutcome outcome must not be null");
            if (outcome == PaymentOutcome.DECLINED && declineReason == null) {
                throw new IllegalArgumentException("GatewayOutcome declineReason is required when outcome is DECLINED");
            }
            if (outcome != PaymentOutcome.DECLINED && declineReason != null) {
                throw new IllegalArgumentException("GatewayOutcome declineReason must be null unless outcome is DECLINED");
            }
        }
    }
}

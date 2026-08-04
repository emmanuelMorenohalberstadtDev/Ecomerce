package com.ecommerce.payment.domain.event;

import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;

import java.time.Instant;

/**
 * Published after a charge attempt is recorded with a {@code DECLINED} outcome
 * ({@code SubmitPaymentUseCase}) — rule 8.
 *
 * <p><strong>Never touches order state</strong> (ADR-0005 items 2/3): the {@link Payment} stays
 * {@code PENDING}, retryable within the checkout payment window; no order-side port is called for
 * this outcome. Published purely for the audit trail — no subscribers exist yet in this change.
 *
 * <p>Payload carries ids and minimal facts, never the full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record PaymentDeclinedEvent(PaymentId paymentId, OrderId orderId, CustomerId customerId,
                                   DeclineReason declineReason, Instant occurredAt) {

    public PaymentDeclinedEvent {
        if (paymentId == null) throw new IllegalArgumentException("paymentId must not be null");
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (declineReason == null) throw new IllegalArgumentException("declineReason must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

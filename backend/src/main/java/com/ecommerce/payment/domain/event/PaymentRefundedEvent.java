package com.ecommerce.payment.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;

/**
 * Published after a {@code Payment} transitions {@code CAPTURED -> REFUNDED}
 * ({@code IssueRefundOnOrderCancelledUseCase}, triggered by {@code OrderCancelledEvent}) —
 * ADR-0005 item 4, rule 7.
 *
 * <p>Published purely for the audit trail — no subscribers exist yet in this change. Payload
 * carries ids and minimal facts, never the full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record PaymentRefundedEvent(PaymentId paymentId, OrderId orderId, CustomerId customerId, Money amount,
                                   Instant occurredAt) {

    public PaymentRefundedEvent {
        if (paymentId == null) throw new IllegalArgumentException("paymentId must not be null");
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

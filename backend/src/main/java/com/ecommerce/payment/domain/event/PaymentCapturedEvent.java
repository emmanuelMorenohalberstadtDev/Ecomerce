package com.ecommerce.payment.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;

/**
 * Published after a {@code Payment} transitions {@code PENDING -> CAPTURED}
 * ({@code SubmitPaymentUseCase}, on a gateway {@code APPROVED} outcome) — rule 8.
 *
 * <p><strong>Not the {@code Order PLACED -> PAID} trigger:</strong> that transition is a
 * synchronous {@code payment -> order} port call
 * ({@code order.application.port.OrderPaymentPort#markPaid}) inside the same transaction that
 * produces this event (ADR-0005 §Decision item 1, correcting domain-model.md §4's earlier
 * "checkout consumes results synchronously" line) — order never subscribes to this event.
 * Published afterward purely for the audit trail (domain-model.md §4: "— (audit; order consumes a
 * successful capture synchronously via a port call, not this event — ADR-0005)").
 *
 * <p>No subscribers exist yet in this change. Payload carries ids and minimal facts, never the
 * full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record PaymentCapturedEvent(PaymentId paymentId, OrderId orderId, CustomerId customerId, Money amount,
                                   Instant occurredAt) {

    public PaymentCapturedEvent {
        if (paymentId == null) throw new IllegalArgumentException("paymentId must not be null");
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

package com.ecommerce.checkout.domain.event;

import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;

/**
 * Published after a {@code CheckoutSession} reaches {@code AWAITING_PAYMENT} with a confirmed
 * stock reservation (ADR-0003 §Decision item 4) — the order hand-off seam. {@code order}, once
 * built, subscribes to this from its own {@code order.application.listener} package (ADR-0003
 * Consequences), following the {@code CartMergeEventListener} precedent. No subscribers exist yet
 * in this change — {@code order} is an empty skeleton; kept for the audit trail and the documented
 * future consumer, exactly like {@code StockReservedEvent}/{@code StockReleasedEvent}.
 *
 * <p>Payload carries ids and minimal facts, never the full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record CheckoutAwaitingPaymentEvent(CheckoutSessionId checkoutSessionId, CustomerId customerId,
                                           CartId cartId, ReservationId reservationId, Money grandTotal,
                                           Instant paymentDeadline, Instant occurredAt) {

    public CheckoutAwaitingPaymentEvent {
        if (checkoutSessionId == null) throw new IllegalArgumentException("checkoutSessionId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (cartId == null) throw new IllegalArgumentException("cartId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (grandTotal == null) throw new IllegalArgumentException("grandTotal must not be null");
        if (paymentDeadline == null) throw new IllegalArgumentException("paymentDeadline must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

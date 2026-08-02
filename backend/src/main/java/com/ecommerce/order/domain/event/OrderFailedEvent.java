package com.ecommerce.order.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;

/**
 * Published after an {@code Order} transitions {@code PLACED -> FAILED} — the payment window
 * lapsed ({@code FailOrderFromCheckoutExpiryUseCase}, rules 5/8) — rule 12.
 *
 * <p><strong>Release already done — by checkout, synchronously, before this order even existed in
 * FAILED status:</strong> checkout's {@code ExpireCheckoutSessionUseCase} already released the
 * reservation via its own {@code ReservationPort} before publishing
 * {@code CheckoutSessionExpiredEvent} (ADR-0003 §Decision item 3). This order-side transition does
 * NOT call {@code ReservationPort.release} again — doing so would hit an
 * already-released-state error (ADR-0004 sanity check). domain-model.md §4: "— (audit; release
 * already done synchronously by checkout)".
 *
 * <p>No subscribers exist yet in this change. Payload carries ids and minimal facts, never the
 * full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderFailedEvent(OrderId orderId, CustomerId customerId, ReservationId reservationId, Instant occurredAt) {

    public OrderFailedEvent {
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

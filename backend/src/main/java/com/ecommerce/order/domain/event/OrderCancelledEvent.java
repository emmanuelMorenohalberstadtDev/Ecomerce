package com.ecommerce.order.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;

/**
 * Published after an {@code Order} transitions to {@code CANCELLED} — customer self-cancel
 * ({@code CancelOrderUseCase}, rule 7: until shipped) or admin lifecycle management
 * ({@code AdminCancelOrderUseCase}) — rule 12.
 *
 * <p><strong>Not the restock trigger:</strong> restocking is a synchronous {@code order ->
 * inventory} port call (via {@code ReservationPort.release}) inside the same transaction that
 * produced this event (ADR-0004 §Decision item 3, correcting domain-model.md §4's earlier
 * "inventory (restock)" consumer listing) — inventory never subscribes to this event. Published
 * afterward for the audit trail.
 *
 * <p><strong>Future consumer:</strong> when {@code payment} is implemented, it becomes this
 * event's first real subscriber (refund), via {@code @TransactionalEventListener(AFTER_COMMIT)} in
 * {@code payment.application.listener} — no new pattern, ADR-0003 item 5 / ADR-0004 Consequences.
 * No subscribers exist yet in this change.
 *
 * <p>Payload carries ids and minimal facts, never the full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderCancelledEvent(OrderId orderId, CustomerId customerId, ReservationId reservationId, Instant occurredAt) {

    public OrderCancelledEvent {
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

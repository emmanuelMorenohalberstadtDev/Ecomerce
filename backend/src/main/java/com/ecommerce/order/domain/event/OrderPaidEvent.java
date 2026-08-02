package com.ecommerce.order.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;

/**
 * Published after an {@code Order} transitions {@code PLACED -> PAID}
 * ({@code AdminMarkOrderPaidUseCase}) — rule 12.
 *
 * <p><strong>Not the reservation-commit trigger:</strong> the reservation commit is a synchronous
 * {@code order -> inventory} port call inside the same transaction that produced this event
 * (ADR-0004 §Decision item 3, correcting domain-model.md §4's earlier "checkout→inventory" naming
 * slip) — inventory never subscribes to this event. Published afterward purely for the audit trail
 * (domain-model.md §4: "— (audit; reservation commit is a synchronous order→inventory port call,
 * ADR-0004)").
 *
 * <p>No subscribers exist yet in this change. Payload carries ids and minimal facts, never the
 * full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderPaidEvent(OrderId orderId, CustomerId customerId, ReservationId reservationId, Instant occurredAt) {

    public OrderPaidEvent {
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

package com.ecommerce.order.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;

/**
 * Published after an {@code Order} is created in {@code PLACED}
 * ({@code PlaceOrderFromCheckoutUseCase}, ADR-0004) — rule 12: every lifecycle fact is recorded.
 *
 * <p>No subscribers exist yet in this change (domain-model.md §4: "— v1 audit; future:
 * notifications") — kept for the audit trail and the documented future consumer, exactly like
 * {@code StockReservedEvent}/{@code StockReleasedEvent} were before order existed.
 *
 * <p>Payload carries ids and minimal facts, never the full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderPlacedEvent(OrderId orderId, CustomerId customerId, ReservationId reservationId, Instant occurredAt) {

    public OrderPlacedEvent {
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

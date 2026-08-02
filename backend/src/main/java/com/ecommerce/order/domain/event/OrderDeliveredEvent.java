package com.ecommerce.order.domain.event;

import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;

import java.time.Instant;

/**
 * Published after an {@code Order} transitions {@code SHIPPED -> DELIVERED}
 * ({@code AdminDeliverOrderUseCase}) — rule 12.
 *
 * <p>No subscribers exist yet in this change (domain-model.md §4: "— (audit; future
 * notifications)"). Payload carries ids and minimal facts, never the full aggregate.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderDeliveredEvent(OrderId orderId, CustomerId customerId, Instant occurredAt) {

    public OrderDeliveredEvent {
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

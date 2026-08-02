package com.ecommerce.order.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One append-only entry in an {@link Order}'s status history (domain-model.md §2/§3
 * {@code OrderStatusTransition}; rule 12: "every transition records timestamp + actor"). Maps 1:1
 * to one {@code order_status_history} row ({@code V006__create_order_schema.sql}).
 *
 * <p>{@code from} is {@code null} only for the very first row — order creation itself
 * ({@code NULL -> PLACED}) has no prior status, matching {@code order_status_history.from_status}'s
 * nullability exactly. Every subsequent transition has both {@code from} and {@code to} populated.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderStatusTransition(OrderStatus from, OrderStatus to, Actor actor, Instant occurredAt) {

    public OrderStatusTransition {
        // from is deliberately nullable — see class javadoc.
        Objects.requireNonNull(to, "OrderStatusTransition to must not be null");
        Objects.requireNonNull(actor, "OrderStatusTransition actor must not be null");
        Objects.requireNonNull(occurredAt, "OrderStatusTransition occurredAt must not be null");
        if (from == to) {
            throw new IllegalArgumentException(
                    "OrderStatusTransition from and to must differ, got: " + to);
        }
    }
}

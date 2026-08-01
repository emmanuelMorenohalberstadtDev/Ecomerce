package com.ecommerce.cart.domain.model;

/**
 * Lifecycle status of a {@link Cart} (domain-model.md §2/§3).
 *
 * <p>Transitions: {@code ACTIVE -> MERGED} (guest cart folded into a customer cart at login,
 * rule 1), {@code ACTIVE -> ORDERED} (checkout completes), {@code ACTIVE -> ABANDONED} (expiry
 * sweep, not implemented in v1). One-directional — a cart never returns to {@code ACTIVE} once
 * it leaves that state. Mirrors {@code carts.status}'s {@code CHECK} constraint in
 * {@code V003__create_cart_schema.sql} exactly (four values, no more).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public enum CartStatus {
    ACTIVE,
    MERGED,
    ORDERED,
    ABANDONED
}

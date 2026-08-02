package com.ecommerce.order.domain.model;

/**
 * The order lifecycle status (domain-model.md §1 order; glossary "Order lifecycle").
 *
 * <p>One-directional state machine: {@code PLACED -> PAID -> CONFIRMED -> SHIPPED -> DELIVERED},
 * with branches {@code PLACED -> FAILED} (payment window lapse, rule 8) and
 * {@code PLACED|PAID|CONFIRMED -> CANCELLED} (customer cancel until shipped, rule 7; admin
 * lifecycle management). The legal-transition set is enforced by {@link Order}'s named domain
 * methods, never by this enum itself — this type only names the seven legal values, matching
 * {@code ck_orders_status} in {@code V006__create_order_schema.sql}.
 */
public enum OrderStatus {
    PLACED,
    PAID,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    FAILED,
    CANCELLED
}

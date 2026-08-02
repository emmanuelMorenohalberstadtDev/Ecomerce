package com.ecommerce.order.domain.model;

/**
 * Who caused a recorded order status transition (domain-model.md §5 glossary "Actor"; §2 order
 * "Actor (customer/admin/system)"). Matches {@code order_status_history.actor_type}'s
 * three-value {@code CHECK} constraint exactly.
 */
public enum ActorType {
    CUSTOMER,
    ADMIN,
    SYSTEM
}

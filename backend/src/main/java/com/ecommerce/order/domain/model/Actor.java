package com.ecommerce.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Who caused a recorded order status transition (domain-model.md §2 order "Actor
 * (customer/admin/system)"; §5 glossary "Actor").
 *
 * <p>{@code actorId} is carried in-memory (e.g. the resolved {@code CurrentActorPort} id for an
 * admin action) but is deliberately NOT persisted beyond {@link #type} —
 * {@code order_status_history} has no {@code actor_id} column
 * ({@code V006__create_order_schema.sql}'s own comment: "who specifically (which admin) is out of
 * this table's documented scope for v1"). {@code actorId} is therefore nullable even for
 * {@link ActorType#CUSTOMER}/{@link ActorType#ADMIN} — callers that don't have or need one may
 * pass {@code null}.
 */
public record Actor(ActorType type, UUID actorId) {

    public Actor {
        Objects.requireNonNull(type, "Actor type must not be null");
    }

    public static Actor customer(UUID customerId) {
        return new Actor(ActorType.CUSTOMER, customerId);
    }

    public static Actor admin(UUID adminId) {
        return new Actor(ActorType.ADMIN, adminId);
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }
}

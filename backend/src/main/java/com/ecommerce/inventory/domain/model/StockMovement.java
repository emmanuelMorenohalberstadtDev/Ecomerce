package com.ecommerce.inventory.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.UUID;

/**
 * One append-only ledger row of the {@code stock_movements} table (database-design.md §3.4/§3.7).
 *
 * <p>Not an aggregate root, not owned by any aggregate — a standalone fact record, exactly like
 * {@code admin_audit_log} rows. Immutable: the whole point of this table is that rows are
 * inserted once and never touched again ({@code StockMovementRepository} exposes insert only).
 *
 * <p>{@code reservationId} is null for {@code ADMIN_ADJUSTMENT} rows (no originating reservation).
 * {@code actorId} is null for system-driven rows ({@code RESERVE}/{@code COMMIT}/{@code RELEASE}
 * — domain-model.md §5 Actor = system) and set to the acting admin's id for
 * {@code ADMIN_ADJUSTMENT} rows. {@code reason} is populated only for {@code ADMIN_ADJUSTMENT}.
 *
 * <p>{@code actorId} is a plain {@link UUID}, not a {@link com.ecommerce.shared.id.UserId} —
 * mirrors {@code stock_movements.actor_id} being a soft reference with no FK to {@code users}
 * (V004 table comment): the auth/user typed id is deliberately not pulled into this
 * cross-context-agnostic ledger row.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record StockMovement(ProductId productId, MovementType movementType, int quantityDelta,
                            ReservationId reservationId, UUID actorId, String reason, Instant occurredAt) {

    public StockMovement {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (movementType == null) {
            throw new IllegalArgumentException("movementType must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}

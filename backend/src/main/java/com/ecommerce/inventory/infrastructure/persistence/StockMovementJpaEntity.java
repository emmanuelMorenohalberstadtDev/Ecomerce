package com.ecommerce.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping one append-only row of the {@code stock_movements} table (database-design.md
 * §3.4/§3.7).
 *
 * <p>{@code id} uses {@code GenerationType.IDENTITY}, matching the column's
 * {@code GENERATED ALWAYS AS IDENTITY} definition — no {@code @Version}, no updatable fields at
 * all (every column is {@code updatable = false}): this entity is only ever inserted, mirroring
 * {@code AdminAuditLogJpaEntity}. {@code reservationId}/{@code actorId}/{@code reason} are
 * nullable soft references (V004 table comment: no FK on any of them).
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "movement_type", nullable = false, updatable = false)
    private String movementType;

    @Column(name = "quantity_delta", nullable = false, updatable = false)
    private int quantityDelta;

    @Column(name = "reservation_id", updatable = false)
    private UUID reservationId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(updatable = false)
    private String reason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    StockMovementJpaEntity() {}

    public StockMovementJpaEntity(UUID productId, String movementType, int quantityDelta,
                                  UUID reservationId, UUID actorId, String reason, Instant createdAt) {
        this.productId = productId;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.reservationId = reservationId;
        this.actorId = actorId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getMovementType() { return movementType; }
    public int getQuantityDelta() { return quantityDelta; }
    public UUID getReservationId() { return reservationId; }
    public UUID getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}

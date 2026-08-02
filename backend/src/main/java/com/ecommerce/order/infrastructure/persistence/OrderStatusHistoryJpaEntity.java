package com.ecommerce.order.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping one row of the append-only {@code order_status_history} table
 * ({@code V006__create_order_schema.sql}).
 *
 * <p>{@code id} is {@code GenerationType.IDENTITY} (bigint identity) — an internal child row,
 * never client-addressed. {@code orderId} is a plain column, not a {@code @ManyToOne}/
 * {@code @JoinColumn} relationship — see {@link OrderJpaEntity}'s class javadoc. Rows are inserted
 * once and never updated or deleted ({@code order_status_history} grants: {@code SELECT, INSERT}
 * only, rule 12; database-design.md §3.7) — this entity has no update path at all.
 *
 * <p>{@code fromStatus} is nullable — {@code null} only for the initial {@code PLACED} row (order
 * creation has no prior status), matching the column exactly.
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "from_status", updatable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;

    OrderStatusHistoryJpaEntity() {}

    public OrderStatusHistoryJpaEntity(UUID orderId, String fromStatus, String toStatus, String actorType,
                                       Instant occurredAt) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActorType() { return actorType; }
    public Instant getOccurredAt() { return occurredAt; }
}

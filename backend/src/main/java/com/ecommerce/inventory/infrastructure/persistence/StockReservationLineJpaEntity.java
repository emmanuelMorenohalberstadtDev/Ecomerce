package com.ecommerce.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * JPA entity mapping one row of the {@code stock_reservation_lines} table.
 *
 * <p>{@code id} is {@code GenerationType.IDENTITY} (bigint identity) — an internal child row,
 * never client-addressed (V004 table comment: "internal child row, never client-addressed as an
 * aggregate root"). No {@code createdAt}/{@code updatedAt} columns — the table has none; lines are
 * inserted once and never updated (V004 §5 grants: {@code SELECT, INSERT} only), so there is
 * nothing to timestamp beyond the parent reservation's own {@code created_at}.
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "stock_reservation_lines")
public class StockReservationLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private StockReservationJpaEntity reservation;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    StockReservationLineJpaEntity() {}

    public StockReservationLineJpaEntity(StockReservationJpaEntity reservation, UUID productId, int quantity) {
        this.reservation = reservation;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public StockReservationJpaEntity getReservation() { return reservation; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}

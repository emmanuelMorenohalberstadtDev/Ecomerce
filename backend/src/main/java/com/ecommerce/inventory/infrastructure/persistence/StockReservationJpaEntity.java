package com.ecommerce.inventory.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping the {@code stock_reservations} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7 before
 *       INSERT (ADR-0002), matching {@code CartJpaEntity}/{@code ProductJpaEntity}.</li>
 *   <li>No {@code @Version} — {@code stock_reservations} carries none in V004 (unlike
 *       {@code stock_items}). With no {@code @Version} and a never-null manually-assigned id,
 *       Spring Data JPA's default new-entity detection falls back to the id-null check, which is
 *       always {@code false} here — {@code save()} always resolves to {@code entityManager.merge(...)},
 *       exactly like {@code ProductJpaEntity} (no {@code Persistable} override needed).</li>
 *   <li><strong>{@code lines} uses {@code cascade = {PERSIST, MERGE}}, deliberately NOT
 *       {@code orphanRemoval} and NOT {@code CascadeType.REMOVE}:</strong> {@code
 *       stock_reservation_lines} grants are {@code SELECT, INSERT} only (V004 §5) — the DB role
 *       cannot run an UPDATE or DELETE against that table at all. {@code JpaStockReservationRepository}
 *       only ever calls {@code save()} on this entity once, from {@code create()}, when the lines
 *       collection is populated for the first and only time (see {@link #addLines}); every later
 *       status transition goes through {@code SpringDataStockReservationDao#updateStatus}, a
 *       header-only {@code @Modifying} query that never loads this entity or its lines at all.
 *       Even so, cascade is scoped to PERSIST/MERGE only (never REMOVE) as a second line of
 *       defence against this entity ever being resaved with a DELETE-triggering mutation to the
 *       lines list.</li>
 *   <li>{@code expiresAt} is nullable, matching the column.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservationJpaEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "checkout_session_id", nullable = false, updatable = false)
    private UUID checkoutSessionId;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "reservation", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    private final List<StockReservationLineJpaEntity> lines = new ArrayList<>();

    StockReservationJpaEntity() {}

    public StockReservationJpaEntity(UUID id, UUID checkoutSessionId, String status,
                                     Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.checkoutSessionId = checkoutSessionId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCheckoutSessionId() { return checkoutSessionId; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<StockReservationLineJpaEntity> getLines() { return lines; }

    /**
     * Populates the (always-empty-until-now) lines collection. Called exactly once, by
     * {@code JpaStockReservationRepository#create}, immediately after constructing a brand-new
     * entity — never used to modify an already-persisted reservation's lines (see class javadoc).
     */
    public void addLines(List<StockReservationLineJpaEntity> newLines) {
        this.lines.addAll(newLines);
    }
}

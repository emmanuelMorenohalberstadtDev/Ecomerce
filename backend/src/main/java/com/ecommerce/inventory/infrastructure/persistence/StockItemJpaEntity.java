package com.ecommerce.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code stock_items} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code productId} is the primary key with no {@code @GeneratedValue} — identifying 1:1
 *       relationship with {@code products}, no surrogate key (database-design.md §4/V004 table
 *       comment). Application code always supplies it.</li>
 *   <li>{@code @Version} maps {@code stock_items.version} for optimistic locking, used only by
 *       the admin load-modify-save adjustment path (database-design.md §7).</li>
 *   <li><strong>{@code implements Persistable<UUID>}, {@code isNew()} always {@code false}:</strong>
 *       same rationale as {@code CartJpaEntity} — this entity has both a manually-assigned
 *       (never-null) id AND a {@code @Version} field, so Spring Data JPA's default new-entity
 *       detection (version == 0 ⇒ "new") would misclassify a freshly-loaded, not-yet-updated row
 *       (version still {@code 0}) as new when {@code JpaStockItemRepository#save} hands it a
 *       brand-new detached entity built from current domain state, re-attempting an INSERT and
 *       colliding on the primary key. Forcing {@code isNew() == false} makes
 *       {@code SimpleJpaRepository.save()} always call {@code entityManager.merge(...)}, which
 *       performs an INSERT or UPDATE based on whether the row actually exists, independent of
 *       this flag, and still honours {@code @Version} for optimistic-lock checking on the
 *       UPDATE path.</li>
 *   <li>The two checkout-critical atomic operations
 *       ({@code decrementIfSufficientStock}/{@code incrementAvailable}) are {@code @Modifying}
 *       bulk JPQL {@code UPDATE}s issued by {@code SpringDataStockItemDao} directly against the
 *       table — they never load or save this entity, and deliberately do not bump
 *       {@code version} (database-design.md §7: "the oversell-critical path uses a conditional
 *       UPDATE with its own row-count success signal ... rather than load-modify-save" — version
 *       is not the concurrency control on that path).</li>
 *   <li>{@code updatedAt} is {@code insertable=false, updatable=false} — managed by the DB
 *       trigger {@code trg_stock_items_updated_at} (fires on every UPDATE, including the bulk
 *       {@code @Modifying} queries, since the trigger is row-level at the DB, independent of the
 *       ORM path that produced the UPDATE).</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "stock_items")
public class StockItemJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "product_id", updatable = false, nullable = false)
    private UUID productId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    StockItemJpaEntity() {}

    public StockItemJpaEntity(UUID productId, int quantityAvailable, long version, Instant createdAt) {
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
        this.version = version;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() { return productId; }

    public UUID getProductId() { return productId; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Always {@code false} — see class javadoc; forces Spring Data JPA to always use {@code merge()}. */
    @Override
    public boolean isNew() {
        return false;
    }
}

package com.ecommerce.catalog.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping the {@code products} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7 before
 *       INSERT (ADR-0002), matching {@code UserAccountJpaEntity}'s convention exactly.</li>
 *   <li>{@code categoryId} is a plain {@code UUID} column, NOT a {@code @ManyToOne} relation to
 *       {@code CategoryJpaEntity} — the domain model deliberately references the owning
 *       category by id only (domain-model.md §2 ownership rules: aggregates never hold object
 *       references to other aggregates), and there is no reason for the JPA mapping to
 *       introduce a coupling the domain explicitly avoids.</li>
 *   <li>{@code status} is a plain {@code String} (not {@code @Enumerated}) so the adapter fully
 *       controls the {@code ACTIVE}/{@code RETIRED} mapping against the DB's
 *       {@code text CHECK (status IN ('ACTIVE','RETIRED'))} column — either approach works here;
 *       plain String keeps this entity trivially inspectable in the debugger/logs.</li>
 *   <li>{@code updatedAt} is {@code insertable=false, updatable=false} — managed by the DB
 *       trigger {@code trg_products_updated_at}, exactly like {@code UserAccountJpaEntity}.</li>
 *   <li>No {@code @Version} column — {@code V002__create_catalog_schema.sql} does not define
 *       one on {@code products}. Optimistic locking for concurrent admin price/detail edits is
 *       therefore not implemented in v1 (flagged in the catalog handoff as a migration
 *       follow-up if concurrent-admin-edit races turn out to matter in practice).</li>
 *   <li>{@code images} is the inverse side of {@link ProductImageJpaEntity#getProduct()};
 *       {@code orphanRemoval = true} plus the adapter's clear-then-rebuild-the-list update
 *       pattern keeps {@code product_images} in sync with the aggregate's image list without a
 *       separate image repository (domain-model.md §2: one repository per aggregate root).</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String status;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(nullable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private final List<ProductImageJpaEntity> images = new ArrayList<>();

    ProductJpaEntity() {}

    public ProductJpaEntity(UUID id, UUID categoryId, String sku, String status,
                            BigDecimal basePrice, String currency, String name, String description,
                            Instant createdAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.sku = sku;
        this.status = status;
        this.basePrice = basePrice;
        this.currency = currency;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCategoryId() { return categoryId; }
    public String getSku() { return sku; }
    public String getStatus() { return status; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getCurrency() { return currency; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ProductImageJpaEntity> getImages() { return images; }

    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public void setStatus(String status) { this.status = status; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Replaces the entire image list in place (clear + rebuild) so
     * {@code orphanRemoval = true} deletes rows dropped from the new list and inserts the rest —
     * simpler and safer than trying to diff/merge individual {@code display_order} slots.
     */
    public void replaceImages(List<ProductImageJpaEntity> newImages) {
        this.images.clear();
        this.images.addAll(newImages);
    }
}

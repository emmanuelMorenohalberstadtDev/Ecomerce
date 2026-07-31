package com.ecommerce.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity mapping one row of the {@code product_images} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} uses {@code GenerationType.IDENTITY} — matches the column's
 *       {@code GENERATED ALWAYS AS IDENTITY} definition. Never client-addressed
 *       (internal child row of the {@code Product} aggregate, ADR-0002 id conventions).</li>
 *   <li>{@code product} is the owning side of the relationship so
 *       {@code ProductJpaEntity} can manage the collection with {@code mappedBy}.</li>
 *   <li>{@code createdAt} is set explicitly at insert time (mirrors
 *       {@code UserAccountJpaEntity}'s {@code createdAt} pattern) rather than relying on the
 *       column's DB-side {@code DEFAULT now()} — the application always supplies it via the
 *       shared {@code Clock} for testability.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "product_images")
public class ProductImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private ProductJpaEntity product;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    ProductImageJpaEntity() {}

    public ProductImageJpaEntity(ProductJpaEntity product, String imageUrl, int displayOrder,
                                 Instant createdAt) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public ProductJpaEntity getProduct() { return product; }
    public String getImageUrl() { return imageUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}

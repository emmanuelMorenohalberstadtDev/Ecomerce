package com.ecommerce.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code categories} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — UUIDv7 generated application-side
 *       (ADR-0002), matching every other aggregate root in this schema.</li>
 *   <li>{@code parentId} is a plain nullable {@code UUID} column, not a self-referencing
 *       {@code @ManyToOne} — the domain model references the parent by {@code CategoryId} only
 *       (never loads the parent {@code Category} object), so there is no need for Hibernate to
 *       manage a self-join relationship here.</li>
 *   <li>{@code updatedAt} is {@code insertable=false, updatable=false} — managed by the DB
 *       trigger {@code trg_categories_updated_at}.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "categories")
public class CategoryJpaEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    CategoryJpaEntity() {}

    public CategoryJpaEntity(UUID id, UUID parentId, String name, Instant createdAt) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getParentId() { return parentId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public void setName(String name) { this.name = name; }
}

package com.ecommerce.catalog.domain.model;

import java.util.Objects;

/**
 * Aggregate root modeling one node in the catalog's category tree.
 *
 * <p>Self-references its parent by {@link CategoryId} only ({@code null} = root category).
 * Categories carry no status/lifecycle of their own in v1 — deletion (guarded at the use-case
 * layer against non-empty categories, see {@code DeleteCategoryUseCase}) is the only removal
 * path, unlike {@code Product} which is never deleted.
 *
 * <p>Created via {@link #create} factory; reconstituted from persistence via
 * {@link #reconstitute}. No public setters — state transitions happen through named domain
 * methods that express intent.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class Category {

    private final CategoryId id;
    private CategoryId parentId;
    private String name;

    private Category(CategoryId id, CategoryId parentId, String name) {
        if (id == null) throw new IllegalArgumentException("CategoryId must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be null or blank");
        }
        if (parentId != null && parentId.equals(id)) {
            throw new IllegalArgumentException("A category cannot be its own parent: " + id);
        }

        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    /** Factory for creating a brand-new category. Assigns a fresh {@link CategoryId}. */
    public static Category create(String name, CategoryId parentId) {
        return new Category(CategoryId.generate(), parentId, name);
    }

    /**
     * Factory for reconstitution from persistence. Restores exact state without re-running
     * creation business rules (beyond the self-parent invariant, which must never hold true
     * regardless of origin).
     */
    public static Category reconstitute(CategoryId id, CategoryId parentId, String name) {
        return new Category(id, parentId, name);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Updates the category's name and/or parent.
     *
     * <p>Only rejects the trivial self-parent case (a category cannot be its own parent).
     * Deeper cycle detection (a category cannot be re-parented under one of its own
     * descendants) requires walking the tree via the repository and is therefore performed by
     * {@code UpdateCategoryUseCase}, which has repository access — a single aggregate instance
     * cannot see the rest of the tree.
     */
    public void updateDetails(String name, CategoryId parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be null or blank");
        }
        if (parentId != null && parentId.equals(this.id)) {
            throw new IllegalArgumentException("A category cannot be its own parent: " + this.id);
        }
        this.name = name;
        this.parentId = parentId;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns {@code true} when this category has no parent (i.e., is a root category). */
    public boolean isRoot() {
        return parentId == null;
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public CategoryId getId() { return id; }

    public CategoryId getParentId() { return parentId; }

    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category category)) return false;
        return Objects.equals(id, category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

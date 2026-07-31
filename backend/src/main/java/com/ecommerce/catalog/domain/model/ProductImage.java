package com.ecommerce.catalog.domain.model;

/**
 * Value object representing one image belonging to a {@link Product}, at a fixed render
 * position.
 *
 * <p>Child of the {@code Product} aggregate — has no existence or identity independent of its
 * owning product (backed by {@code product_images}, which cascades on the parent's delete).
 * Equality and identity are purely by value ({@code imageUrl} + {@code displayOrder}); the
 * database-generated {@code bigint} row id is an infrastructure concern the domain never sees.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record ProductImage(String imageUrl, int displayOrder) {

    public ProductImage {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("ProductImage imageUrl must not be null or blank");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException(
                    "ProductImage displayOrder must be >= 0, got: " + displayOrder);
        }
    }
}

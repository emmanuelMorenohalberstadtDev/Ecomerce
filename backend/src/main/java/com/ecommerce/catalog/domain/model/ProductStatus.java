package com.ecommerce.catalog.domain.model;

/**
 * Availability status of a {@link Product}.
 *
 * <p>{@code ACTIVE} products are sellable and appear in public browse/search.
 * {@code RETIRED} products are excluded from public browse/search but the row (and any
 * historical order/cart references) is never deleted (domain-model.md §1 catalog, rule 10).
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free.
 */
public enum ProductStatus {
    ACTIVE,
    RETIRED
}

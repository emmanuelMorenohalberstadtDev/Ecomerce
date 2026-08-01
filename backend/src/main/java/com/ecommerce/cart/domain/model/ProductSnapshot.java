package com.ecommerce.cart.domain.model;

import com.ecommerce.shared.money.Money;

/**
 * Value object capturing a product's display data — name and unit price — at the moment a
 * {@link CartLine} is added to a cart (domain-model.md §2/§5 "Product snapshot").
 *
 * <p><strong>Hard rule:</strong> a snapshot is copied once, at add-to-cart time, and is never
 * re-fetched or re-derived from catalog on read. Catalog price/name changes must never silently
 * mutate an existing cart line (database-design.md §3.2). Checkout, not cart, is responsible for
 * re-pricing against the live catalog.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record ProductSnapshot(String name, Money unitPrice) {

    public ProductSnapshot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ProductSnapshot name must not be null or blank");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("ProductSnapshot unitPrice must not be null");
        }
    }
}

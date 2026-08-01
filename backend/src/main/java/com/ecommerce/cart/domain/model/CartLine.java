package com.ecommerce.cart.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Instant;

/**
 * Internal entity of the {@link Cart} aggregate — one line per product (domain-model.md §2/§3).
 *
 * <p>Immutable value-style record, consistent with {@code catalog.domain.model.ProductImage}:
 * {@link Cart} never mutates a {@code CartLine} in place — every domain operation that changes a
 * line (quantity change, unavailability flag, merge) replaces it with a new {@code CartLine}
 * instance in the aggregate's internal list. {@code Cart} is the only class that constructs or
 * replaces {@code CartLine}s during a domain operation; infrastructure reconstructs a whole
 * {@code Cart} (lines included) via {@link Cart#reconstitute}.
 *
 * <p>{@code addedAt} is the line's own "added to cart" timestamp (maps to {@code cart_items.created_at}).
 * It is preserved verbatim across quantity/unavailability updates to the same line — only a
 * brand-new line (or the tie-break case in {@link Cart#mergeFrom}) gets a fresh value — because
 * it is the input to {@code mergeFrom}'s "freshest wins" tie-break rule.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record CartLine(ProductId productId, ProductSnapshot snapshot, Quantity quantity,
                        boolean unavailable, Instant addedAt) {

    public CartLine {
        if (productId == null) {
            throw new IllegalArgumentException("CartLine productId must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("CartLine snapshot must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("CartLine quantity must not be null");
        }
        if (addedAt == null) {
            throw new IllegalArgumentException("CartLine addedAt must not be null");
        }
    }
}

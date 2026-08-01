package com.ecommerce.inventory.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

/**
 * Internal entity of the {@link StockReservation} aggregate — one reserved quantity per product
 * under one reservation (domain-model.md §2/§3 {@code ReservedLine}; maps to
 * {@code stock_reservation_lines}).
 *
 * <p>Immutable value-style record, consistent with {@code catalog.domain.model.ProductImage} and
 * {@code cart.domain.model.CartLine}: written once at reservation creation and never mutated —
 * mirrors {@code stock_reservation_lines}' insert-only grants (V004 §5: "SELECT, INSERT ONLY").
 * There is no method here that updates a line's quantity; {@link StockReservation} never exposes
 * one either.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record ReservedLine(ProductId productId, Quantity quantity) {

    public ReservedLine {
        if (productId == null) {
            throw new IllegalArgumentException("ReservedLine productId must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("ReservedLine quantity must not be null");
        }
    }
}

package com.ecommerce.inventory.application.usecase;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

/**
 * One requested (product, quantity) pair in a {@link ReserveStockUseCase} batch — mirrors
 * catalog's {@code ProductImageInput} pattern of a small standalone command-input record shared
 * across a use case's public API.
 */
public record ReservationLineInput(ProductId productId, Quantity quantity) {

    public ReservationLineInput {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("quantity must not be null");
        }
    }
}

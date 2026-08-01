package com.ecommerce.inventory.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when a stock decrement cannot be satisfied: either the atomic conditional decrement
 * behind {@link com.ecommerce.inventory.domain.port.out.StockItemRepository#decrementIfSufficientStock}
 * affected zero rows (reservation shortage, rule 6), or an admin adjustment's negative delta
 * would drive {@code quantity_available} below zero ({@link com.ecommerce.inventory.domain.model.StockItem#adjust}).
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic {@code ConflictException}
 * handler — api-guidelines.md §3.2 registers a more specific {@code insufficient-stock} slug, but
 * wiring a per-exception-type slug requires editing {@code ApiExceptionHandler}, which this
 * change is not permitted to do (mirrors cart's {@code CartNotActiveException} javadoc making the
 * identical trade-off for {@code cart-not-active}). Flagged in the inventory handoff report.
 */
public final class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String message) {
        super(message);
    }
}

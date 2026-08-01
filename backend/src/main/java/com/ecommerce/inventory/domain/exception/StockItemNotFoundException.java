package com.ecommerce.inventory.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when an admin stock adjustment targets a product with no {@code stock_items} row yet.
 *
 * <p>Deliberately does <strong>not</strong> auto-create the row (see the inventory handoff report
 * for the open question this raises: nothing in this codebase creates the initial
 * {@code stock_items} row for a newly-created product — catalog's {@code CreateProductUseCase}
 * does not touch inventory, and inventing an implicit "first adjustment creates the row" rule
 * here was judged too large a business-rule assumption to make silently).
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public final class StockItemNotFoundException extends NotFoundException {

    public StockItemNotFoundException(String message) {
        super(message);
    }
}

package com.ecommerce.cart.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when add-to-cart targets a product that catalog reports as nonexistent or not ACTIVE
 * (retired).
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}, not 404: the cart resource being
 * mutated ("my cart") exists and is correctly addressed — the request body's {@code productId}
 * is the thing that turns out unusable, which is "well-formed but semantically rejected by a
 * business rule" (api-guidelines.md §2), not "the addressed resource does not exist". Catalog's
 * own {@code GetProductUseCase} treats RETIRED as indistinguishable from nonexistent on its
 * public surface; {@link com.ecommerce.catalog.application.port.ProductLookupPort#findActiveById}
 * mirrors that, so this single exception naturally covers both cases without the cart context
 * needing to know which one occurred.
 */
public final class ProductNotAvailableException extends BusinessRuleViolationException {

    public ProductNotAvailableException(String message) {
        super(message);
    }
}

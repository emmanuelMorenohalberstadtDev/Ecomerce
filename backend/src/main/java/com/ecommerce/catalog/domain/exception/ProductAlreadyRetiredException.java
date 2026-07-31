package com.ecommerce.catalog.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when {@code RetireProductUseCase} is invoked for a product whose status is already
 * {@code RETIRED}.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler} — a state conflict ("duplicate
 * transition", api-guidelines §2 decision table), not a validation error and not a generic
 * business-rule rejection. See {@link com.ecommerce.catalog.domain.model.Product#retire()}
 * javadoc for why re-retirement is rejected rather than treated as an idempotent no-op.
 */
public final class ProductAlreadyRetiredException extends ConflictException {

    public ProductAlreadyRetiredException(String message) {
        super(message);
    }
}

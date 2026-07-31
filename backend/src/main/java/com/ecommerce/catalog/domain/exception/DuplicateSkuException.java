package com.ecommerce.catalog.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when an admin attempts to create a product with a SKU that already identifies another
 * product in the catalog.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}. Raised in two layers, defense-in-depth:
 * (1) {@code CreateProductUseCase} pre-checks {@code ProductRepository.existsBySku} before
 * insert (mirrors auth's {@code existsByEmail} pattern); (2) the JPA adapter translates a raw
 * {@code uq_products_sku} constraint violation into this exception as a backstop against the
 * race between the pre-check and the insert (two concurrent admin requests for the same new
 * SKU).
 */
public final class DuplicateSkuException extends ConflictException {

    public DuplicateSkuException(String message) {
        super(message);
    }

    public DuplicateSkuException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.ecommerce.catalog.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when a requested category cannot be found — either the target of a direct lookup, or
 * a {@code categoryId}/{@code parentId} referenced by a product/category mutation that does not
 * exist.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public final class CategoryNotFoundException extends NotFoundException {

    public CategoryNotFoundException(String message) {
        super(message);
    }
}

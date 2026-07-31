package com.ecommerce.catalog.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when a requested product cannot be found, or must not be revealed to the caller.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}. Also used to hide a {@code RETIRED}
 * product from non-admin callers on the public read endpoints (security-architecture §3.4 seed
 * row: "ACTIVE products only for non-admin") — from the caller's perspective a retired product
 * simply does not exist, exactly like an unowned resource is 404'd elsewhere in the system.
 */
public final class ProductNotFoundException extends NotFoundException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}

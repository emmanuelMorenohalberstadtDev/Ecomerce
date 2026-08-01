package com.ecommerce.cart.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown by the JPA adapter when saving a {@code Cart} loses the optimistic-lock race on the
 * {@code carts.version} column (database-design.md §7: "a stale write raises
 * OptimisticLockException -> 409").
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic
 * {@code ConflictException} handler — mirrors how catalog's {@code DuplicateSkuException}
 * translates a raw {@code DataIntegrityViolationException} at the persistence boundary rather
 * than letting a framework exception leak past the adapter (hexagonal skill: "adapters translate
 * errors: vendor exceptions become domain results/exceptions at the boundary").
 */
public final class CartConcurrentModificationException extends ConflictException {

    public CartConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

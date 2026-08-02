package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown by the JPA adapter when saving a {@code CheckoutSession} loses the optimistic-lock race
 * on the {@code checkout_sessions.version} column (database-design.md §7: "a stale write raises
 * OptimisticLockException -> 409"). Mirrors {@code cart.domain.exception.CartConcurrentModificationException}
 * exactly.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic {@code ConflictException}
 * handler — adapters translate vendor exceptions into domain exceptions at the boundary (hexagonal
 * skill), rather than letting a framework exception leak past the adapter.
 */
public final class CheckoutSessionConcurrentModificationException extends ConflictException {

    public CheckoutSessionConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

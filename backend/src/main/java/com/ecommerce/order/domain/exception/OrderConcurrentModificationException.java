package com.ecommerce.order.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown by the JPA adapter when saving an {@link com.ecommerce.order.domain.model.Order} loses
 * the optimistic-lock race on the {@code orders.version} column
 * ({@code V006__create_order_schema.sql}: "a stale concurrent status-transition UPDATE raises
 * OptimisticLockException -> 409"). Mirrors
 * {@code checkout.domain.exception.CheckoutSessionConcurrentModificationException} exactly.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic {@code ConflictException}
 * handler — adapters translate vendor exceptions into domain exceptions at the boundary
 * (hexagonal skill), rather than letting a framework exception leak past the adapter.
 */
public final class OrderConcurrentModificationException extends ConflictException {

    public OrderConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

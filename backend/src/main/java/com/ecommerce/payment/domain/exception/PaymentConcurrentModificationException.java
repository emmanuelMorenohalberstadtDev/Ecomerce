package com.ecommerce.payment.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown by the JPA adapter when saving a {@link com.ecommerce.payment.domain.model.Payment} loses
 * the optimistic-lock race on the {@code payments.version} column
 * ({@code V007__create_payment_schema.sql}: "a stale concurrent UPDATE (e.g. a retried attempt
 * racing a refund) raises OptimisticLockException -> 409"). Mirrors
 * {@code order.domain.exception.OrderConcurrentModificationException} exactly.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic {@code ConflictException}
 * handler — adapters translate vendor exceptions into domain exceptions at the boundary (hexagonal
 * skill), rather than letting a framework exception leak past the adapter.
 */
public final class PaymentConcurrentModificationException extends ConflictException {

    public PaymentConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

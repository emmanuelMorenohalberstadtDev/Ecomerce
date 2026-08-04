package com.ecommerce.payment.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when a {@link com.ecommerce.payment.domain.model.Payment} state-transition method
 * ({@code recordAttempt}, {@code markCaptured}, {@code markRefunded}) is called while the payment
 * is not in a legal source state for that operation — the one-way transition graph
 * ({@code PENDING -> CAPTURED -> REFUNDED}) forbids it. Mirrors
 * {@code order.domain.exception.InvalidOrderTransitionException} exactly.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}'s generic {@code BusinessRuleViolationException}
 * handler.
 */
public final class InvalidPaymentStateException extends BusinessRuleViolationException {

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}

package com.ecommerce.order.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when an {@link com.ecommerce.order.domain.model.Order} state-transition method
 * ({@code markPaid}, {@code confirm}, {@code ship}, {@code deliver}, {@code cancel}, {@code fail})
 * is called while the order is not in a legal source state for that transition — the one-way
 * transition graph (domain-model.md §1: {@code PLACED -> PAID -> CONFIRMED -> SHIPPED -> DELIVERED},
 * branches {@code PLACED -> FAILED} and {@code PLACED|PAID|CONFIRMED -> CANCELLED}) forbids it.
 * Mirrors {@code checkout.domain.exception.InvalidCheckoutSessionStateException} exactly.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}'s generic {@code BusinessRuleViolationException}
 * handler (well-formed request, semantically rejected by a business rule — api-guidelines §2).
 */
public final class InvalidOrderTransitionException extends BusinessRuleViolationException {

    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}

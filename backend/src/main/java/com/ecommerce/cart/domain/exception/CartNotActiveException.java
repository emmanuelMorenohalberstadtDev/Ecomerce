package com.ecommerce.cart.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when a mutating operation ({@code addItem}, {@code updateLineQuantity},
 * {@code removeLine}, {@code mergeFrom}) targets a {@link com.ecommerce.cart.domain.model.Cart}
 * whose status is not {@code ACTIVE}.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler} (the shared, generic
 * {@code BusinessRuleViolationException} handler — every subtype maps to the same
 * {@code business-rule-violation} problem {@code type} today; see the cart handoff report for
 * why the more specific {@code cart-not-active} slug in api-guidelines.md §3.2 is not yet wired
 * without editing {@code ApiExceptionHandler}, which this change is not permitted to touch).
 */
public final class CartNotActiveException extends BusinessRuleViolationException {

    public CartNotActiveException(String message) {
        super(message);
    }
}

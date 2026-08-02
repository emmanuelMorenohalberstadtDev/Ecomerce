package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when {@code StartCheckoutUseCase} cannot start a checkout session because the customer
 * has no ACTIVE cart, or that cart has no lines.
 *
 * <p>Modeled as a business-rule violation (422), not a 404 — mirrors
 * {@code cart.domain.exception.CartNotActiveException}'s "cart-not-active-style semantics"
 * (api-guidelines.md §2: "well-formed but semantically rejected by a business rule"), not
 * "the addressed resource does not exist": the caller addresses no cart id directly (checkout
 * starts from "my cart", exactly like cart's own "my cart" endpoints resolve identity, not a
 * path id) — the request is well-formed, the customer's cart situation just does not support
 * checkout yet.
 */
public final class CartNotCheckoutableException extends BusinessRuleViolationException {

    public CartNotCheckoutableException(String message) {
        super(message);
    }
}

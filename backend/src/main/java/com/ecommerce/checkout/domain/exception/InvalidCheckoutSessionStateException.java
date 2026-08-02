package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when a {@link com.ecommerce.checkout.domain.model.CheckoutSession} state-transition
 * method ({@code moveToAwaitingPayment}, {@code expire}, {@code refreshRecalculatedTotal}) is
 * called while the session is not in the required source state — the one-way transition graph
 * ({@code PENDING_CONFIRMATION -> AWAITING_PAYMENT -> EXPIRED}, ADR-0003 §Decision item 3/4)
 * forbids it. Mirrors {@code inventory.domain.exception.InvalidReservationStateException} exactly.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}'s generic {@code BusinessRuleViolationException}
 * handler (well-formed request, semantically rejected by a business rule — api-guidelines §2).
 */
public final class InvalidCheckoutSessionStateException extends BusinessRuleViolationException {

    public InvalidCheckoutSessionStateException(String message) {
        super(message);
    }
}

package com.ecommerce.inventory.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when {@link com.ecommerce.inventory.domain.model.StockReservation#commit()} or
 * {@link com.ecommerce.inventory.domain.model.StockReservation#release()} is called on a
 * reservation that is not currently {@code HELD} — the one-way transition graph
 * (domain-model.md §2: {@code HELD -> COMMITTED} or {@code HELD -> RELEASED}, never any other
 * edge) forbids it.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}'s generic {@code BusinessRuleViolationException}
 * handler (well-formed request, semantically rejected by a business rule — api-guidelines §2).
 */
public final class InvalidReservationStateException extends BusinessRuleViolationException {

    public InvalidReservationStateException(String message) {
        super(message);
    }
}

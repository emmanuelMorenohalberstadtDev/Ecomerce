package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when a {@link com.ecommerce.checkout.domain.model.CheckoutSession} referenced by id does
 * not exist, or exists but does not belong to the requesting {@code CustomerId} —
 * ownership-scoped repository lookups (security-architecture §3.2/§3.3: "the repository query
 * scopes by owner so unowned data is unfetchable"; "ownership failures are 404, never 403") return
 * empty for both cases identically, so this single exception naturally covers both, mirroring
 * {@code cart.domain.exception.CartNotFoundException}.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public final class CheckoutSessionNotFoundException extends NotFoundException {

    public CheckoutSessionNotFoundException(String message) {
        super(message);
    }
}

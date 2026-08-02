package com.ecommerce.order.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when an {@link com.ecommerce.order.domain.model.Order} referenced by id does not exist,
 * or exists but does not belong to the requesting {@code CustomerId} — ownership-scoped repository
 * lookups (security-architecture §3.2/§3.3: "the repository query scopes by owner so unowned data
 * is unfetchable"; "ownership failures are 404, never 403") return empty for both cases
 * identically, so this single exception naturally covers both, mirroring
 * {@code checkout.domain.exception.CheckoutSessionNotFoundException}.
 *
 * <p>Also used for admin lookups by bare id (no ownership scoping) — a genuinely nonexistent order
 * id.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public final class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}

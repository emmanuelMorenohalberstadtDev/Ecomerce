package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when {@code StartCheckoutUseCase} is called with an {@code Idempotency-Key} that has
 * already been used to start a checkout session (api-guidelines.md §5.3: "same key + different
 * body -> 409"; simplified per this change's documented idempotency design — see
 * {@code StartCheckoutUseCase} javadoc — to "any replay of an already-used key is a 409", without
 * a cheap-detection fast path for an exact-replay-returns-original-result case).
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s generic {@code ConflictException} handler.
 * Also the translation target for a lost race against
 * {@code uq_checkout_sessions_idempotency} (two concurrent requests with the same key).
 */
public final class DuplicateIdempotencyKeyException extends ConflictException {

    public DuplicateIdempotencyKeyException(String message) {
        super(message);
    }
}

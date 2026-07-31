package com.ecommerce.common.web.exception;

/**
 * Signals a state conflict — the resource exists but the requested mutation is incompatible with
 * its current state.
 *
 * <p>Maps to HTTP 409. Typical use: duplicate registration attempt, optimistic-lock conflict
 * surfaced as a business error rather than a low-level retry.
 *
 * <p>Context-specific subtypes are {@code final} and live in their bounded context's
 * {@code domain.exception} package.
 */
public non-sealed class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

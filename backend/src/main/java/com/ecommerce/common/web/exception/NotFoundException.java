package com.ecommerce.common.web.exception;

/**
 * Signals that a requested resource does not exist (or must not be revealed to the caller).
 *
 * <p>Maps to HTTP 404. Ownership-based 404s (returning "not found" when the caller lacks access to
 * an existing resource) follow the anti-enumeration rule defined in {@code security-architecture.md
 * §3.3} — this exception type is used for both cases so the handler emits a uniform 404 body.
 *
 * <p>Context-specific subtypes (e.g. {@code ProductNotFoundException}) are {@code final} and live
 * in their bounded context's {@code domain.exception} package.
 */
public non-sealed class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

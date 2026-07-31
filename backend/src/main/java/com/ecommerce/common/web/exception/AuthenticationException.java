package com.ecommerce.common.web.exception;

/**
 * Signals an authentication failure that should map to HTTP 401.
 *
 * <p>Subtypes live in bounded contexts' {@code domain.exception} packages.
 * Examples: {@code InvalidCredentialsException}, {@code AccountLockedException}.
 *
 * <p>Kept in {@code common.web.exception} so {@link com.ecommerce.common.web.ApiExceptionHandler}
 * can match on this type without importing context-specific exception classes — preserving
 * the invariant that {@code common} does not depend on any bounded context.
 */
public non-sealed class AuthenticationException extends DomainException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

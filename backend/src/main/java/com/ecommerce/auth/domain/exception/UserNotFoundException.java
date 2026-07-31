package com.ecommerce.auth.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when a requested user cannot be found (or must not be revealed per ownership rules).
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}. Anti-enumeration: the login and
 * credential-check paths must NOT throw this exception to the caller; they use a generic
 * {@code InvalidCredentialsException} instead. This exception is safe on internal paths
 * (e.g., finding a user by ID for a profile operation) where the caller already proved identity.
 */
public final class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(String message) {
        super(message);
    }
}

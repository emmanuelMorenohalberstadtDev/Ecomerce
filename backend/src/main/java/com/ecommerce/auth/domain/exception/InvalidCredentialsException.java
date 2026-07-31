package com.ecommerce.auth.domain.exception;

import com.ecommerce.common.web.exception.AuthenticationException;

/**
 * Thrown when authentication fails due to invalid credentials (user not found or
 * wrong password — both cases are intentionally indistinguishable to the caller).
 *
 * <p>Maps to HTTP 401 via {@link com.ecommerce.common.web.ApiExceptionHandler}.
 * The generic message is identical whether the user exists or not (anti-enumeration §2.2).
 * BCrypt timing is equalized separately in {@code LoginUseCase} via the dummy-hash technique.
 */
public final class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

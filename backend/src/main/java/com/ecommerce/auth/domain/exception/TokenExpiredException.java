package com.ecommerce.auth.domain.exception;

import com.ecommerce.common.web.exception.TokenException;

/**
 * Thrown when a refresh token or password-reset token has expired, is not found,
 * or otherwise cannot be used.
 *
 * <p>On refresh endpoints: maps to HTTP 401 (client must re-authenticate).
 * On password-reset/consume: maps to HTTP 400 with slug {@code invalid-or-expired-token}.
 * The {@link com.ecommerce.common.web.ApiExceptionHandler} selects the status based on
 * the request path to avoid conflating the two contexts.
 */
public final class TokenExpiredException extends TokenException {

    public TokenExpiredException(String message) {
        super(message);
    }
}

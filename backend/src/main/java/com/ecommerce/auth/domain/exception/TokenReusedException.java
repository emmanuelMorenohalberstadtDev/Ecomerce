package com.ecommerce.auth.domain.exception;

import com.ecommerce.common.web.exception.TokenException;

/**
 * Thrown when a refresh token that was already rotated (marked used) is presented again.
 *
 * <p>This is the theft detection signal (security §2.3). Before this exception propagates,
 * the entire token family is revoked by the use case. The client receives HTTP 401 and
 * must re-authenticate from scratch.
 */
public final class TokenReusedException extends TokenException {

    public TokenReusedException(String message) {
        super(message);
    }
}

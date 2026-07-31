package com.ecommerce.auth.domain.exception;

import com.ecommerce.common.web.exception.AuthenticationException;

/**
 * Thrown when a login attempt is made on a locked account.
 *
 * <p>Maps to HTTP 401 via {@link com.ecommerce.common.web.ApiExceptionHandler}
 * with the same generic body as {@code InvalidCredentialsException} — the caller
 * cannot distinguish locked vs. wrong-password (anti-enumeration §2.6).
 */
public final class AccountLockedException extends AuthenticationException {

    public AccountLockedException(String message) {
        super(message);
    }
}

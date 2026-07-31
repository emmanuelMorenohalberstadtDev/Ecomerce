package com.ecommerce.common.web.exception;

/**
 * Signals a token-related failure (expired, reused, invalid) that should map to
 * HTTP 401 on token endpoints or HTTP 400 on password-reset endpoints.
 *
 * <p>Subtypes: {@code TokenExpiredException}, {@code TokenReusedException} in the
 * auth bounded context's {@code domain.exception} package.
 *
 * <p>Lives in {@code common.web.exception} so {@link com.ecommerce.common.web.ApiExceptionHandler}
 * can match on it without importing auth-context types.
 */
public non-sealed class TokenException extends DomainException {

    public TokenException(String message) {
        super(message);
    }

    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

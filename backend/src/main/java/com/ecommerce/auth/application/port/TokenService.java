package com.ecommerce.auth.application.port;

import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.shared.id.UserId;

/**
 * Outbound port for JWT access token generation and a raw refresh token factory.
 *
 * <p>Keeps the application layer free of infrastructure types (JWT library, SecureRandom).
 * The infrastructure adapter ({@code JwtTokenServiceAdapter}) implements this port by
 * delegating to {@link com.ecommerce.auth.infrastructure.security.JwtTokenService} and
 * {@link com.ecommerce.auth.infrastructure.security.RawRefreshToken}.
 */
public interface TokenService {

    /**
     * Issues a signed JWT access token for the given user.
     *
     * @param userId the user's identity (becomes the {@code sub} claim)
     * @param role   the user's role (stored as a custom claim)
     * @return compact JWT string
     */
    String generateAccessToken(UserId userId, Role role);

    /**
     * Generates a new opaque high-entropy refresh token value.
     *
     * @return the raw token value to deliver to the client (never store this — store the hash)
     */
    String generateRawRefreshToken();

    /**
     * Returns the SHA-256 hex hash of the given raw token value, suitable for DB storage.
     *
     * @param rawToken the plaintext token
     * @return 64-character lowercase hex string
     */
    String hashRefreshToken(String rawToken);
}

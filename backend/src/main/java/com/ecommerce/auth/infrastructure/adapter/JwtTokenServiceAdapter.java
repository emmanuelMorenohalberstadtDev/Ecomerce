package com.ecommerce.auth.infrastructure.adapter;

import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.auth.infrastructure.security.RawRefreshToken;
import com.ecommerce.auth.infrastructure.security.TokenHasher;
import com.ecommerce.shared.id.UserId;

/**
 * Adapter implementing the application-layer {@link TokenService} port.
 *
 * <p>Delegates JWT generation to {@link JwtTokenService} and refresh token
 * generation/hashing to {@link RawRefreshToken}/{@link TokenHasher}.
 * This keeps infrastructure classes out of the application layer.
 */
public class JwtTokenServiceAdapter implements TokenService {

    private final JwtTokenService jwtTokenService;

    public JwtTokenServiceAdapter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public String generateAccessToken(UserId userId, Role role) {
        return jwtTokenService.generateAccessToken(userId, role);
    }

    @Override
    public String generateRawRefreshToken() {
        return RawRefreshToken.generate().value();
    }

    @Override
    public String hashRefreshToken(String rawToken) {
        return TokenHasher.sha256Hex(rawToken);
    }
}

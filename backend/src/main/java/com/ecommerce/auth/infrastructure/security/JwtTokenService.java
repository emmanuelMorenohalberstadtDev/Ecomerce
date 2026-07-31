package com.ecommerce.auth.infrastructure.security;

import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.infrastructure.config.JwtProperties;
import com.ecommerce.shared.id.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates signed JWT access tokens.
 *
 * <p>Algorithm: HS256, pinned — never read from the token header (security §6b).
 * The jjwt 0.12.x library rejects {@code alg:none} by default.
 *
 * <p>Access token TTL: configured via {@code jwt.access-token-expiry-minutes} (default 10 min
 * per security §2.3). No PII in claims — only {@code sub} (UserId), {@code roles}, {@code jti}.
 *
 * <p>Issuer and audience are validated on parse to prevent cross-service token reuse (one
 * issuer = one verifier in v1 modular monolith; this rule is preparatory for RS256 migration).
 */
@Component
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    static final String CLAIM_ROLES = "roles";
    static final String ISSUER = "ecommerce-api";
    static final String AUDIENCE = "ecommerce-spa";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMinutes;
    private final Clock clock;

    public JwtTokenService(JwtProperties jwtProperties, Clock clock) {
        // Key must be at least 256 bits for HS256 — enforced by jjwt; startup fails otherwise
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMinutes = jwtProperties.accessTokenExpiryMinutes();
        this.clock = clock;
    }

    /**
     * Generates a signed HS256 access token for the given user.
     *
     * @param userId the user's identity (stored as {@code sub})
     * @param role   the user's role (stored as {@code roles} claim)
     * @return a compact JWT string ready for delivery to the client
     */
    public String generateAccessToken(UserId userId, Role role) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(accessTokenExpiryMinutes * 60L);

        return Jwts.builder()
                .subject(userId.value().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim(CLAIM_ROLES, role.name())
                .id(UUID.randomUUID().toString()) // jti
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)             // HS256 is inferred from SecretKey
                .compact();
    }

    /**
     * Validates a token's signature, expiry, issuer, and audience; extracts claims.
     *
     * @param token the compact JWT string from the Authorization header
     * @return extracted claims
     * @throws TokenExpiredException    if the token has expired
     * @throws IllegalArgumentException if the token is malformed, has wrong issuer/audience,
     *                                  or fails signature verification
     */
    public JwtClaims validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .clockSkewSeconds(60L) // tolerate up to 60s clock drift (security §6b)
                    .clock(() -> Date.from(clock.instant())) // honor the injected Clock, not wall-clock
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UserId userId = UserId.of(claims.getSubject());
            Role role = Role.valueOf(claims.get(CLAIM_ROLES, String.class));
            return new JwtClaims(userId, role);

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            throw new TokenExpiredException("Access token has expired");
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid access token: " + e.getMessage(), e);
        }
    }

    /**
     * Validated claims extracted from a JWT access token.
     */
    public record JwtClaims(UserId userId, Role role) {}
}

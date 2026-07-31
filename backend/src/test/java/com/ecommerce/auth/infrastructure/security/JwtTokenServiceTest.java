package com.ecommerce.auth.infrastructure.security;

import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.infrastructure.config.JwtProperties;
import com.ecommerce.shared.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenService}.
 *
 * <p>Uses a fixed {@link Clock} so token expiry is deterministic.
 * No Spring context — the service is constructed directly with test properties.
 *
 * <p>Security contracts tested:
 * <ul>
 *   <li>Generated tokens validate correctly (round-trip).</li>
 *   <li>Expired tokens throw {@link TokenExpiredException}.</li>
 *   <li>Tampered signatures throw {@link IllegalArgumentException}.</li>
 *   <li>{@code alg:none} is rejected by jjwt 0.12.x by default.</li>
 *   <li>Claims carry {@code userId} and {@code role} — no PII.</li>
 * </ul>
 */
@Tag("unit")
class JwtTokenServiceTest {

    // Minimum 32 ASCII chars (256 bits) required for HS256
    private static final String TEST_SECRET = "test-secret-key-for-junit-tests-minimum-32-chars!!";
    private static final long EXPIRY_MINUTES = 10L;

    private Clock clock;
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);
        JwtProperties props = new JwtProperties(TEST_SECRET, EXPIRY_MINUTES);
        service = new JwtTokenService(props, clock);
    }

    // ── generate + validate roundtrip ─────────────────────────────────────────

    @Test
    void shouldValidateSuccessfully_whenTokenIsValid() {
        UserId userId = UserId.generate();

        String token = service.generateAccessToken(userId, Role.CUSTOMER);
        JwtTokenService.JwtClaims claims = service.validateAndExtract(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void shouldIncludeUserId_inSubjectClaim() {
        UserId userId = UserId.generate();

        String token = service.generateAccessToken(userId, Role.CUSTOMER);
        JwtTokenService.JwtClaims claims = service.validateAndExtract(token);

        assertThat(claims.userId().value()).isEqualTo(userId.value());
    }

    @Test
    void shouldIncludeRole_inRolesClaim() {
        UserId userId = UserId.generate();

        String token = service.generateAccessToken(userId, Role.ADMIN);
        JwtTokenService.JwtClaims claims = service.validateAndExtract(token);

        assertThat(claims.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldGenerateValidToken_forCustomerRole() {
        UserId userId = UserId.generate();

        String token = service.generateAccessToken(userId, Role.CUSTOMER);
        JwtTokenService.JwtClaims claims = service.validateAndExtract(token);

        assertThat(claims.role()).isEqualTo(Role.CUSTOMER);
    }

    // ── expired token ──────────────────────────────────────────────────────────

    @Test
    void shouldThrowTokenExpiredException_whenTokenHasExpired() {
        UserId userId = UserId.generate();

        // Generate token at "now"
        String token = service.generateAccessToken(userId, Role.CUSTOMER);

        // Advance clock past the 10-minute expiry + 60s clock skew tolerance
        Clock futureClock = Clock.fixed(
                clock.instant().plusSeconds(EXPIRY_MINUTES * 60 + 120),
                ZoneOffset.UTC);
        JwtTokenService futureService = new JwtTokenService(
                new JwtProperties(TEST_SECRET, EXPIRY_MINUTES), futureClock);

        assertThatThrownBy(() -> futureService.validateAndExtract(token))
                .isInstanceOf(TokenExpiredException.class);
    }

    // ── tampered signature ─────────────────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenSignatureIsManipulated() {
        UserId userId = UserId.generate();
        String validToken = service.generateAccessToken(userId, Role.CUSTOMER);

        // Corrupt the last character of the signature (base64 part after second '.')
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + "X";

        assertThatThrownBy(() -> service.validateAndExtract(tamperedToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenTokenIsCompletelyMalformed() {
        assertThatThrownBy(() -> service.validateAndExtract("not.a.jwt.at.all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenTokenIsSignedWithDifferentKey() {
        JwtProperties otherProps = new JwtProperties(
                "other-secret-key-for-junit-tests-minimum-32-chars!!", EXPIRY_MINUTES);
        JwtTokenService otherService = new JwtTokenService(otherProps, clock);
        UserId userId = UserId.generate();

        String tokenFromOtherKey = otherService.generateAccessToken(userId, Role.CUSTOMER);

        // Our service must reject a token signed with a different key
        assertThatThrownBy(() -> service.validateAndExtract(tokenFromOtherKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── alg:none rejection ─────────────────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenAlgNoneTokenPresented() {
        // Craft a minimal unsigned JWT (alg:none). jjwt 0.12.x rejects this by default
        // because parseSignedClaims() requires a signature.
        // Base64URL: {"alg":"none","typ":"JWT"}.{"sub":"test"}.
        String algNoneToken = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
                + ".eyJzdWIiOiJ0ZXN0In0"
                + ".";

        assertThatThrownBy(() -> service.validateAndExtract(algNoneToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── compact format ─────────────────────────────────────────────────────────

    @Test
    void shouldReturnCompactString_withThreeDotSeparatedParts() {
        String token = service.generateAccessToken(UserId.generate(), Role.CUSTOMER);

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        // All three parts must be non-empty
        assertThat(parts[0]).isNotEmpty();
        assertThat(parts[1]).isNotEmpty();
        assertThat(parts[2]).isNotEmpty();
    }
}

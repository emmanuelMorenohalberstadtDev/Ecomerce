package com.ecommerce.cart.infrastructure.security;

import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer;
import com.ecommerce.cart.domain.security.GuestTokenHasher;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mints a brand-new, high-entropy guest cart token.
 *
 * <p>Mirrors {@code auth.infrastructure.security.RawRefreshToken}'s generation approach exactly
 * (same {@code SecureRandom} byte count, same URL-safe Base64 encoding) rather than reusing that
 * class directly — security-architecture §2.7 only requires guest cart tokens to be
 * {@code ≥128-bit SecureRandom}, not literally the same class as refresh tokens. Hashing delegates
 * to {@link GuestTokenHasher} — see that class's javadoc for why it is an independent
 * re-implementation of {@code auth.infrastructure.security.TokenHasher}'s algorithm rather than a
 * direct reuse (an ArchUnit cycle-detection constraint, not a design preference). Kept as cart's
 * own class so cart's infrastructure has no dependency on auth's infrastructure package (would
 * violate {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}).
 *
 * <p>32 bytes = 256 bits, well above the 128-bit minimum.
 */
public class GuestCartTokenGenerator implements GuestCartTokenIssuer {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    @Override
    public IssuedGuestToken issue() {
        byte[] bytes = new byte[BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = GuestTokenHasher.sha256Hex(raw);
        return new IssuedGuestToken(raw, hash);
    }
}

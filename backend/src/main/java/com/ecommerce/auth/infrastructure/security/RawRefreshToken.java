package com.ecommerce.auth.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * An opaque, high-entropy refresh token value for delivery to the client.
 *
 * <p>Uses {@link SecureRandom} to generate 32 bytes (256 bits) of randomness —
 * well above the 128-bit minimum required by security-architecture §6a. UUIDv7
 * is explicitly NOT used for bearer secrets (§6a condition).
 *
 * <p>The raw value is URL-safe Base64-encoded so it can be transmitted in a
 * cookie or JSON body without encoding issues. It is NEVER stored at rest;
 * only its SHA-256 hash is persisted.
 *
 * @param value URL-safe Base64-encoded raw token (deliver to client)
 */
public record RawRefreshToken(String value) {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int BYTES = 32; // 256 bits

    public RawRefreshToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RawRefreshToken value must not be null or blank");
        }
    }

    /** Generates a new cryptographically strong refresh token. */
    public static RawRefreshToken generate() {
        byte[] bytes = new byte[BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return new RawRefreshToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * Returns the SHA-256 hex digest of this token's value — the form stored in the DB.
     */
    public String sha256Hex() {
        return TokenHasher.sha256Hex(value);
    }

    /** Overridden to prevent accidental logging of the raw token. */
    @Override
    public String toString() {
        return "[REFRESH_TOKEN]";
    }
}

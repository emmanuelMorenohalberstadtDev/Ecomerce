package com.ecommerce.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for SHA-256 hashing of opaque token values before DB storage.
 *
 * <p>All token types (refresh, password-reset) are stored as their SHA-256 hex digest.
 * The plaintext token is delivered to the client and never retained server-side
 * (security-architecture §2.3, §2.6).
 *
 * <p>Non-instantiable — static utility only.
 */
public final class TokenHasher {

    private TokenHasher() {}

    /**
     * Returns the lowercase hexadecimal SHA-256 digest of {@code input} encoded as UTF-8.
     *
     * @param input the raw token value; must not be null
     * @return 64-character hex string
     */
    public static String sha256Hex(String input) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this never happens
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

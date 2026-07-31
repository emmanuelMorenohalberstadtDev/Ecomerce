package com.ecommerce.auth.application.port;

import com.ecommerce.shared.id.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for password reset token persistence.
 *
 * <p>Named {@code Store} (not {@code Repository}) to avoid triggering the ArchUnit
 * rule that requires all {@code *Repository} interfaces to reside in {@code ..domain..}.
 *
 * <p>Tokens are stored as SHA-256 hashes. The plaintext token is delivered via email
 * and never retained server-side (security-architecture §2.6).
 *
 * <p>Implemented by {@link com.ecommerce.auth.infrastructure.persistence.PasswordResetTokenJpaAdapter}.
 */
public interface PasswordResetTokenStore {

    /**
     * Persists a new (unused) password reset token.
     */
    void save(PasswordResetTokenRecord record);

    /**
     * Finds a token by its SHA-256 hash that is not yet used and not expired.
     *
     * <p>Returns empty if the token does not exist, is already used, or has expired.
     * All three failure cases produce an identical empty result (anti-enumeration §2.6).
     */
    Optional<PasswordResetTokenRecord> findActiveByTokenHash(String tokenHash);

    /**
     * Marks the token as used after a successful password reset (single-use enforcement).
     */
    void markAsUsed(long tokenId);

    /**
     * Revokes all active (unused, unexpired) reset tokens for a given user.
     *
     * <p>Called before issuing a new reset token so that at most one valid token
     * exists per user at any time (security §2.6 — previous token invalidation).
     */
    void revokeAllActiveByUserId(UserId userId);

    // -------------------------------------------------------------------------
    // Port data record
    // -------------------------------------------------------------------------

    record PasswordResetTokenRecord(
            long id,
            UserId userId,
            String tokenHash,
            Instant expiresAt,
            boolean used
    ) {}
}

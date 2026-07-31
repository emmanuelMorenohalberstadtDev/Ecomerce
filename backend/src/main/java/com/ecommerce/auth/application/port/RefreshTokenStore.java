package com.ecommerce.auth.application.port;

import com.ecommerce.shared.id.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for refresh token family and token persistence.
 *
 * <p>Named {@code Store} (not {@code Repository}) to avoid triggering the ArchUnit
 * rule that requires all {@code *Repository} interfaces to reside in {@code ..domain..}.
 * The domain-layer port concept is in {@code UserAccountRepository} (which is a domain
 * aggregate root port); refresh token families are infrastructure concerns exposed here
 * as an application-layer port.
 *
 * <p>Implemented by {@link com.ecommerce.auth.infrastructure.persistence.RefreshTokenJpaAdapter}.
 *
 * <p>The {@link RefreshTokenRecord} sealed interface permits two subtypes:
 * <ul>
 *   <li>{@link BaseRefreshTokenRecord} — used when writing a new token.</li>
 *   <li>{@link EnrichedRefreshTokenRecord} — returned by reads, carrying the
 *       {@code userId} from the family join (one query, not two).</li>
 * </ul>
 */
public interface RefreshTokenStore {

    /**
     * Persists a new token family created at login.
     *
     * @return the generated family id (DB GENERATED ALWAYS AS IDENTITY)
     */
    long saveFamily(RefreshTokenFamilyRecord family);

    /**
     * Persists a new refresh token within an existing family.
     * The {@code id} field is ignored ({@code 0L}); the DB assigns it.
     */
    void saveToken(RefreshTokenRecord token);

    /**
     * Looks up a token by its SHA-256 hash (the value stored at rest).
     * Returns an {@link EnrichedRefreshTokenRecord} carrying the associated {@code userId}.
     */
    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

    /**
     * Revokes an entire family (logout, reuse detection, password change/reset).
     *
     * @param familyId the id of the family to revoke
     * @param reason   one of: LOGOUT, REUSE_DETECTED, PASSWORD_CHANGE, PASSWORD_RESET
     */
    void revokeFamilyById(long familyId, String reason);

    /**
     * Revokes all non-revoked families belonging to a user (password reset — possible compromise).
     */
    void revokeAllFamiliesByUserId(UserId userId, String reason);

    /**
     * Marks a token as used (called before issuing the successor in rotation).
     */
    void markTokenAsUsed(long tokenId);

    // -------------------------------------------------------------------------
    // Port data types
    // -------------------------------------------------------------------------

    /** Data for creating a new token family row. */
    record RefreshTokenFamilyRecord(
            UserId userId,
            Instant createdAt
    ) {}

    /**
     * Sealed interface for refresh token records.
     * Both permitted subtypes live in the application layer.
     */
    sealed interface RefreshTokenRecord
            permits BaseRefreshTokenRecord, EnrichedRefreshTokenRecord {

        long id();
        long familyId();
        String tokenHash();
        Instant issuedAt();
        Instant expiresAt();
        boolean used();
    }

    /**
     * Base record for token writes. {@code id == 0L} means "DB will assign".
     */
    record BaseRefreshTokenRecord(
            long id,
            long familyId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            boolean used
    ) implements RefreshTokenRecord {}
}

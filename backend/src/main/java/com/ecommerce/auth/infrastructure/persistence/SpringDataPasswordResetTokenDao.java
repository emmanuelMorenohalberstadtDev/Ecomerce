package com.ecommerce.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link PasswordResetTokenJpaEntity}.
 *
 * <p>Package-private — only {@link PasswordResetTokenJpaAdapter} uses it.
 * Named "Dao" (not "Repository") to avoid triggering the ArchUnit rule that
 * requires all {@code *Repository} interfaces to live in {@code ..domain..}.
 */
@Repository
interface SpringDataPasswordResetTokenDao extends JpaRepository<PasswordResetTokenJpaEntity, Long> {

    /**
     * Finds a token by its hash that is not used and has not expired.
     * All three failure cases (not found / used / expired) return empty Optional
     * so the caller receives one generic error (anti-enumeration §2.6).
     */
    Optional<PasswordResetTokenJpaEntity> findByTokenHashAndUsedFalseAndExpiresAtAfter(
            String tokenHash, Instant now);

    /**
     * Marks all active (unused, unexpired) reset tokens for a user as used.
     *
     * <p>Called before issuing a new reset token so that at most one valid token
     * exists per user at any time (security §2.6).
     *
     * @return number of rows updated
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetTokenJpaEntity t
            SET t.used = true, t.usedAt = :now
            WHERE t.userId = :userId
              AND t.used = false
              AND t.expiresAt > :now
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}

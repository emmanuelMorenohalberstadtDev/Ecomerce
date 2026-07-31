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
 * Spring Data JPA interface for {@link RefreshTokenJpaEntity}.
 *
 * <p>Package-private — only {@link RefreshTokenJpaAdapter} uses it.
 * Named "Dao" (not "Repository") to avoid triggering the ArchUnit rule that
 * requires all {@code *Repository} interfaces to live in {@code ..domain..}.
 *
 * <p>The {@link #findEnrichedByTokenHash} query joins the family table to retrieve
 * {@code userId} so the use case needs no second round-trip.
 */
@Repository
interface SpringDataRefreshTokenDao extends JpaRepository<RefreshTokenJpaEntity, Long> {

    /**
     * Finds a refresh token by its hash, joining the family to retrieve {@code userId}.
     */
    @Query("""
            SELECT t.id AS id,
                   t.familyId AS familyId,
                   f.userId AS userId,
                   t.tokenHash AS tokenHash,
                   t.issuedAt AS issuedAt,
                   t.expiresAt AS expiresAt,
                   t.used AS used
            FROM RefreshTokenJpaEntity t
            JOIN RefreshTokenFamilyJpaEntity f ON f.id = t.familyId
            WHERE t.tokenHash = :tokenHash
              AND f.revoked = false
            """)
    Optional<EnrichedProjection> findEnrichedByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Marks a specific token as used in a single UPDATE.
     */
    @Modifying
    @Query("""
            UPDATE RefreshTokenJpaEntity t
            SET t.used = true, t.usedAt = :at
            WHERE t.id = :id
            """)
    void markUsed(@Param("id") Long id, @Param("at") Instant at);

    /**
     * JPQL projection interface for the join query.
     */
    interface EnrichedProjection {
        Long getId();
        Long getFamilyId();
        UUID getUserId();
        String getTokenHash();
        Instant getIssuedAt();
        Instant getExpiresAt();
        boolean isUsed();
    }
}

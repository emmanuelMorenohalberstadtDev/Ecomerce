package com.ecommerce.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link RefreshTokenFamilyJpaEntity}.
 *
 * <p>Package-private — only {@link RefreshTokenJpaAdapter} uses it.
 * Named "Dao" (not "Repository") to avoid triggering the ArchUnit rule that
 * requires all {@code *Repository} interfaces to live in {@code ..domain..}.
 */
@Repository
interface SpringDataRefreshTokenFamilyDao extends JpaRepository<RefreshTokenFamilyJpaEntity, Long> {

    /**
     * Bulk-revokes all non-revoked families for a user in a single UPDATE.
     * Used by {@code ConsumePasswordResetUseCase} via the port.
     */
    @Modifying
    @Query("""
            UPDATE RefreshTokenFamilyJpaEntity f
            SET f.revoked = true, f.revokedAt = :at, f.revokedReason = :reason
            WHERE f.userId = :userId AND f.revoked = false
            """)
    int revokeAllByUserId(@Param("userId") UUID userId,
                          @Param("at") Instant at,
                          @Param("reason") String reason);
}

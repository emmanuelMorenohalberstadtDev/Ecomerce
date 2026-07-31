package com.ecommerce.auth.application.port;

import com.ecommerce.shared.id.UserId;

import java.time.Instant;

/**
 * Enriched refresh token record that carries the {@code userId} from the
 * {@code refresh_token_families} join.
 *
 * <p>Returned by {@link RefreshTokenStore#findByTokenHash}. The JPA adapter in
 * the infrastructure layer creates instances of this record (infrastructure →
 * application dependency, which is permitted by the layering rules). The use
 * case pattern-matches on this type to access {@code userId} without a second query.
 */
public record EnrichedRefreshTokenRecord(
        long id,
        long familyId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        boolean used,
        UserId userId
) implements RefreshTokenStore.RefreshTokenRecord {}

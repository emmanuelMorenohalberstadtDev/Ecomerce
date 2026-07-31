package com.ecommerce.auth.infrastructure.persistence;

import com.ecommerce.auth.application.port.EnrichedRefreshTokenRecord;
import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.shared.id.UserId;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * JPA adapter implementing the application-layer {@link RefreshTokenStore} port.
 *
 * <p>Coordinates two Spring Data DAOs ({@link SpringDataRefreshTokenFamilyDao}
 * and {@link SpringDataRefreshTokenDao}) to fulfill the port contract.
 *
 * <p>{@link #findByTokenHash} returns an {@link EnrichedRefreshTokenRecord} populated
 * from a JPQL join query, providing the {@code userId} without a second DB call.
 */
public class RefreshTokenJpaAdapter implements RefreshTokenStore {

    private final SpringDataRefreshTokenFamilyDao familyDao;
    private final SpringDataRefreshTokenDao tokenDao;
    private final Clock clock;

    public RefreshTokenJpaAdapter(SpringDataRefreshTokenFamilyDao familyDao,
                                  SpringDataRefreshTokenDao tokenDao,
                                  Clock clock) {
        this.familyDao = familyDao;
        this.tokenDao = tokenDao;
        this.clock = clock;
    }

    @Override
    public long saveFamily(RefreshTokenFamilyRecord family) {
        RefreshTokenFamilyJpaEntity entity =
                new RefreshTokenFamilyJpaEntity(family.userId().value(), family.createdAt());
        return familyDao.save(entity).getId();
    }

    @Override
    public void saveToken(RefreshTokenRecord token) {
        tokenDao.save(new RefreshTokenJpaEntity(
                token.familyId(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt()
        ));
    }

    @Override
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return tokenDao.findEnrichedByTokenHash(tokenHash)
                .map(p -> new EnrichedRefreshTokenRecord(
                        p.getId(),
                        p.getFamilyId(),
                        p.getTokenHash(),
                        p.getIssuedAt(),
                        p.getExpiresAt(),
                        p.isUsed(),
                        UserId.of(p.getUserId())
                ));
    }

    @Override
    public void revokeFamilyById(long familyId, String reason) {
        familyDao.findById(familyId).ifPresent(family -> {
            family.revoke(clock.instant(), reason);
            familyDao.save(family);
        });
    }

    @Override
    public void revokeAllFamiliesByUserId(UserId userId, String reason) {
        Instant now = clock.instant();
        familyDao.revokeAllByUserId(userId.value(), now, reason);
    }

    @Override
    public void markTokenAsUsed(long tokenId) {
        tokenDao.markUsed(tokenId, clock.instant());
    }
}

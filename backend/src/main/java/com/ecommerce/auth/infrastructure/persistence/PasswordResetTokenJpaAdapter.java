package com.ecommerce.auth.infrastructure.persistence;

import com.ecommerce.auth.application.port.PasswordResetTokenStore;
import com.ecommerce.shared.id.UserId;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * JPA adapter implementing the application-layer {@link PasswordResetTokenStore} port.
 */
public class PasswordResetTokenJpaAdapter implements PasswordResetTokenStore {

    private final SpringDataPasswordResetTokenDao dao;
    private final Clock clock;

    public PasswordResetTokenJpaAdapter(SpringDataPasswordResetTokenDao dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @Override
    public void save(PasswordResetTokenRecord record) {
        dao.save(new PasswordResetTokenJpaEntity(
                record.userId().value(),
                record.tokenHash(),
                record.expiresAt(),
                clock.instant()
        ));
    }

    @Override
    public Optional<PasswordResetTokenRecord> findActiveByTokenHash(String tokenHash) {
        return dao.findByTokenHashAndUsedFalseAndExpiresAtAfter(tokenHash, clock.instant())
                .map(e -> new PasswordResetTokenRecord(
                        e.getId(),
                        UserId.of(e.getUserId()),
                        e.getTokenHash(),
                        e.getExpiresAt(),
                        e.isUsed()
                ));
    }

    @Override
    public void markAsUsed(long tokenId) {
        dao.findById(tokenId).ifPresent(e -> {
            e.markUsed(clock.instant());
            dao.save(e);
        });
    }

    @Override
    public void revokeAllActiveByUserId(UserId userId) {
        Instant now = clock.instant();
        dao.revokeAllActiveByUserId(userId.value(), now);
    }
}

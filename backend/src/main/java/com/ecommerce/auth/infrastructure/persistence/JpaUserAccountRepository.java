package com.ecommerce.auth.infrastructure.persistence;

import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.shared.id.UserId;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link UserAccountRepository} port.
 *
 * <p>Maps between {@link UserAccount} (domain) and {@link UserAccountJpaEntity} (JPA).
 * The domain object never touches JPA internals; the JPA entity never escapes this adapter.
 */
public class JpaUserAccountRepository implements UserAccountRepository {

    private final SpringDataUserDao springDataUserDao;
    private final Clock clock;

    public JpaUserAccountRepository(SpringDataUserDao springDataUserDao, Clock clock) {
        this.springDataUserDao = springDataUserDao;
        this.clock = clock;
    }

    @Override
    public UserAccount save(UserAccount user) {
        UserAccountJpaEntity entity = toEntity(user);
        UserAccountJpaEntity saved = springDataUserDao.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<UserAccount> findById(UserId id) {
        return springDataUserDao.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(Email email) {
        return springDataUserDao.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return springDataUserDao.existsByEmail(email.value());
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private UserAccountJpaEntity toEntity(UserAccount user) {
        Instant now = clock.instant();
        return new UserAccountJpaEntity(
                user.getId().value(),
                user.getEmail().value(),
                user.getPasswordHash().value(), // raw hash — never log this field
                user.getRole(),
                user.isAccountLocked(),
                user.getFailedLoginAttempts(),
                now // createdAt: ignored on UPDATE because updatable=false
        );
    }

    private UserAccount toDomain(UserAccountJpaEntity entity) {
        return UserAccount.reconstitute(
                UserId.of(entity.getId()),
                new Email(entity.getEmail()),
                new PasswordHash(entity.getPasswordHash()),
                entity.getRole(),
                entity.isAccountLocked(),
                entity.getFailedLoginAttempts()
        );
    }
}

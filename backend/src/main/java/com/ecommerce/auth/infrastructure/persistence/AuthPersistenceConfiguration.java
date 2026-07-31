package com.ecommerce.auth.infrastructure.persistence;

import com.ecommerce.auth.application.port.PasswordResetTokenStore;
import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Persistence-layer wiring for the auth context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private Spring Data
 * DAO interfaces without making them public — preserving the encapsulation that prevents
 * other packages from bypassing the adapter layer.
 */
@Configuration
public class AuthPersistenceConfiguration {

    @Bean
    public UserAccountRepository userAccountRepository(
            SpringDataUserDao springDataUserDao,
            Clock clock) {
        return new JpaUserAccountRepository(springDataUserDao, clock);
    }

    @Bean
    public RefreshTokenStore refreshTokenStore(
            SpringDataRefreshTokenFamilyDao familyDao,
            SpringDataRefreshTokenDao tokenDao,
            Clock clock) {
        return new RefreshTokenJpaAdapter(familyDao, tokenDao, clock);
    }

    @Bean
    public PasswordResetTokenStore passwordResetTokenStore(
            SpringDataPasswordResetTokenDao dao,
            Clock clock) {
        return new PasswordResetTokenJpaAdapter(dao, clock);
    }
}

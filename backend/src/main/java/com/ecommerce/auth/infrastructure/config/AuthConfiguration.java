package com.ecommerce.auth.infrastructure.config;

import com.ecommerce.auth.application.port.PasswordResetTokenStore;
import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.application.usecase.ConsumePasswordResetUseCase;
import com.ecommerce.auth.application.usecase.LoginUseCase;
import com.ecommerce.auth.application.usecase.LogoutUseCase;
import com.ecommerce.auth.application.usecase.RefreshTokenUseCase;
import com.ecommerce.auth.application.usecase.RegisterUserUseCase;
import com.ecommerce.auth.application.usecase.RequestPasswordResetUseCase;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.auth.infrastructure.adapter.JwtTokenServiceAdapter;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * Wiring for the auth bounded context's application-layer beans.
 *
 * <p>Persistence-adapter beans ({@link UserAccountRepository}, {@link RefreshTokenStore},
 * {@link PasswordResetTokenStore}) are created in
 * {@link com.ecommerce.auth.infrastructure.persistence.AuthPersistenceConfiguration}
 * (same package as the package-private Spring Data DAO interfaces).
 *
 * <p>This class wires the token service adapter and all use cases.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfiguration {

    @Bean
    public TokenService tokenService(JwtTokenService jwtTokenService) {
        return new JwtTokenServiceAdapter(jwtTokenService);
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new RegisterUserUseCase(userAccountRepository, passwordEncoder, eventPublisher, clock);
    }

    @Bean
    public LoginUseCase loginUseCase(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RefreshTokenStore refreshTokenStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new LoginUseCase(userAccountRepository, passwordEncoder, tokenService,
                refreshTokenStore, eventPublisher, clock);
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(
            RefreshTokenStore refreshTokenStore,
            UserAccountRepository userAccountRepository,
            TokenService tokenService,
            Clock clock) {
        return new RefreshTokenUseCase(refreshTokenStore, userAccountRepository,
                tokenService, clock);
    }

    @Bean
    public LogoutUseCase logoutUseCase(
            RefreshTokenStore refreshTokenStore,
            TokenService tokenService) {
        return new LogoutUseCase(refreshTokenStore, tokenService);
    }

    @Bean
    public RequestPasswordResetUseCase requestPasswordResetUseCase(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenStore passwordResetTokenStore,
            TokenService tokenService,
            Clock clock) {
        return new RequestPasswordResetUseCase(userAccountRepository,
                passwordResetTokenStore, tokenService, clock);
    }

    @Bean
    public ConsumePasswordResetUseCase consumePasswordResetUseCase(
            PasswordResetTokenStore passwordResetTokenStore,
            UserAccountRepository userAccountRepository,
            RefreshTokenStore refreshTokenStore,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        return new ConsumePasswordResetUseCase(passwordResetTokenStore, userAccountRepository,
                refreshTokenStore, passwordEncoder, tokenService);
    }
}

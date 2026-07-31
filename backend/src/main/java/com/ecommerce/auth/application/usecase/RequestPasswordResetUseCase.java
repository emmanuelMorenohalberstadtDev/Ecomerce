package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.PasswordResetTokenStore;
import com.ecommerce.auth.application.port.PasswordResetTokenStore.PasswordResetTokenRecord;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Initiates a password reset by creating a short-lived single-use token.
 *
 * <p><strong>Anti-enumeration (security §2.6):</strong> ALWAYS returns void (202 to the
 * client) regardless of whether the email is registered. Both branches exit at the same
 * point with no observable difference.
 *
 * <p>In v1 "sending the email" is a console log only. The token is logged at DEBUG level
 * which MUST be disabled in production (security §2.8 log-scrubbing rules).
 *
 * <p>The token uses the same 256-bit SecureRandom entropy as refresh tokens (security §6a:
 * bearer secrets must not use UUIDv7).
 */
@Service
@Transactional
public class RequestPasswordResetUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetUseCase.class);

    /** Password reset token TTL per security §2.6 (≤ 1 hour). */
    static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final TokenService tokenService;
    private final Clock clock;

    public RequestPasswordResetUseCase(UserAccountRepository userAccountRepository,
                                       PasswordResetTokenStore passwordResetTokenStore,
                                       TokenService tokenService,
                                       Clock clock) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.passwordResetTokenStore = Objects.requireNonNull(passwordResetTokenStore);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.clock = Objects.requireNonNull(clock);
    }

    public void execute(RequestPasswordResetCommand command) {
        Email email = new Email(command.email());
        Instant now = clock.instant();

        userAccountRepository.findByEmail(email).ifPresent(user -> {
            // Invalidate any existing active tokens before issuing a new one
            // (security §2.6 — at most one valid reset token per user at any time).
            passwordResetTokenStore.revokeAllActiveByUserId(user.getId());

            String rawToken = tokenService.generateRawRefreshToken(); // same entropy class
            String tokenHash = tokenService.hashRefreshToken(rawToken);

            passwordResetTokenStore.save(new PasswordResetTokenRecord(
                    0L,
                    user.getId(),
                    tokenHash,
                    now.plus(TOKEN_TTL),
                    false
            ));

            // v1: log to console only — replace with real mailer in production
            log.info("Password reset requested for userId={}", user.getId());
        });

        // Both branches (user found / not found) return silently here — anti-enumeration
    }

    public record RequestPasswordResetCommand(String email) {}
}

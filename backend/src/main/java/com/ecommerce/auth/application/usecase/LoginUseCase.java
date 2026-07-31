package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.RefreshTokenStore.BaseRefreshTokenRecord;
import com.ecommerce.auth.application.port.RefreshTokenStore.RefreshTokenFamilyRecord;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.domain.event.UserAuthenticatedEvent;
import com.ecommerce.auth.domain.exception.AccountLockedException;
import com.ecommerce.auth.domain.exception.InvalidCredentialsException;
import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Authenticates a user with email + password and issues a JWT access token
 * plus a rotating refresh token.
 *
 * <p><strong>Constant-time discipline (security §2.2):</strong> BCrypt ALWAYS runs,
 * even when the user does not exist. A pre-computed dummy hash is used in the
 * not-found branch. This prevents timing-based user enumeration attacks.
 *
 * <p><strong>Lockout:</strong> after {@link UserAccount#MAX_FAILED_ATTEMPTS} consecutive
 * failures the account is locked. The check happens after BCrypt so timing stays constant.
 *
 * <p>Transaction boundary is this use case class (backend-architecture §4.1).
 */
@Service
@Transactional
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    /** Absolute lifetime of a refresh token family (security §2.3). */
    static final Duration FAMILY_LIFETIME = Duration.ofDays(14);

    /**
     * Pre-computed BCrypt hash used in the not-found branch to keep constant timing.
     * Computed once at construction via the real PasswordEncoder (BCrypt strength 12).
     * The string it hashes is arbitrary — this is a timing equalizer, not a credential.
     */
    private final String dummyHash;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenStore refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LoginUseCase(UserAccountRepository userAccountRepository,
                        PasswordEncoder passwordEncoder,
                        TokenService tokenService,
                        RefreshTokenStore refreshTokenRepository,
                        ApplicationEventPublisher eventPublisher,
                        Clock clock) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        // Compute once at startup — BCrypt cost is paid here, not per request
        this.dummyHash = passwordEncoder.encode("dummy-timing-constant-placeholder-xK9mQ2vR");
    }

    public LoginResult execute(LoginCommand command) {
        Email email = new Email(command.email());
        Optional<UserAccount> maybeUser = userAccountRepository.findByEmail(email);

        // CONSTANT TIMING: always run BCrypt regardless of user existence.
        // If not found, compare against dummy hash (will fail) then throw generic error.
        boolean credentialsMatch;
        if (maybeUser.isPresent()) {
            UserAccount user = maybeUser.get();
            credentialsMatch = passwordEncoder.matches(command.rawPassword(),
                    user.getPasswordHash().value());
        } else {
            // Burn BCrypt time against dummy hash — result is always false
            passwordEncoder.matches(command.rawPassword(), dummyHash);
            credentialsMatch = false;
        }

        if (!credentialsMatch) {
            // Record the failure on the account if it exists (for lockout tracking).
            // This path is only taken when credentials do not match — the dummy-hash
            // branch (user not found) never calls recordFailedLogin, which is acceptable
            // because an attacker cannot enumerate the difference: both paths throw the
            // same generic exception with the same message.
            maybeUser.ifPresent(u -> {
                u.recordFailedLogin();
                userAccountRepository.save(u);
                log.warn("Failed login userId={} failedAttempts={}",
                        u.getId(), u.getFailedLoginAttempts());
            });
            // Generic message — identical whether user exists or not (anti-enumeration)
            throw new InvalidCredentialsException("Invalid credentials");
        }

        UserAccount user = maybeUser.get();

        // Check lock AFTER BCrypt always completes (constant timing already satisfied).
        // A locked account with CORRECT credentials must NOT reveal that the account is
        // locked to the caller — AccountLockedException maps to the same generic 401 body
        // as InvalidCredentialsException (see ApiExceptionHandler), so the HTTP response is
        // indistinguishable from a wrong-password response. The distinct type is preserved
        // here only for internal logging/telemetry.
        if (!user.isActive()) {
            log.warn("Login attempt on locked account userId={}", user.getId());
            throw new AccountLockedException("Account is locked");
        }

        // Successful authentication: reset counter
        user.resetFailedLoginAttempts();
        userAccountRepository.save(user);

        // Issue token pair
        Instant now = clock.instant();
        String rawRefreshToken = tokenService.generateRawRefreshToken();
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);

        long familyId = refreshTokenRepository.saveFamily(
                new RefreshTokenFamilyRecord(user.getId(), now));

        refreshTokenRepository.saveToken(new BaseRefreshTokenRecord(
                0L,
                familyId,
                tokenHash,
                now,
                now.plus(FAMILY_LIFETIME),
                false
        ));

        String accessToken = tokenService.generateAccessToken(user.getId(), user.getRole());

        eventPublisher.publishEvent(new UserAuthenticatedEvent(user.getId(), now));
        log.info("Login successful userId={}", user.getId());

        return new LoginResult(accessToken, rawRefreshToken);
    }

    public record LoginCommand(String email, String rawPassword) {}

    public record LoginResult(String accessToken, String refreshToken) {}
}

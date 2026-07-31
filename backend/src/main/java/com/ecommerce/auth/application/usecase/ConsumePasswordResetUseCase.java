package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.PasswordResetTokenStore;
import com.ecommerce.auth.application.port.PasswordResetTokenStore.PasswordResetTokenRecord;
import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Consumes a password-reset token, updates the user's password, and revokes all sessions.
 *
 * <p>Protocol:
 * <ol>
 *   <li>Hash the presented token → look up active (unused, unexpired) record.</li>
 *   <li>Not found: generic 400 "invalid-or-expired-token" (anti-enumeration §2.6).</li>
 *   <li>Load the associated user account.</li>
 *   <li>Encode and set the new password via the domain method.</li>
 *   <li>Mark the reset token as used.</li>
 *   <li>Revoke ALL refresh token families for this user (security §2.4 — possible compromise).</li>
 * </ol>
 *
 * <p>All steps run in a single transaction — an action without its revocation trail must
 * not commit (backend-architecture §4.6 principle applied here).
 */
@Service
@Transactional
public class ConsumePasswordResetUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConsumePasswordResetUseCase.class);

    private final PasswordResetTokenStore passwordResetTokenStore;
    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public ConsumePasswordResetUseCase(PasswordResetTokenStore passwordResetTokenStore,
                                       UserAccountRepository userAccountRepository,
                                       RefreshTokenStore refreshTokenStore,
                                       PasswordEncoder passwordEncoder,
                                       TokenService tokenService) {
        this.passwordResetTokenStore = Objects.requireNonNull(passwordResetTokenStore);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.tokenService = Objects.requireNonNull(tokenService);
    }

    public void execute(ConsumePasswordResetCommand command) {
        String tokenHash = tokenService.hashRefreshToken(command.token());

        // Step 1–2: find active token; generic error if not found/used/expired
        PasswordResetTokenRecord resetToken = passwordResetTokenStore.findActiveByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenExpiredException("Invalid or expired password reset token"));

        // Step 3: load user
        // Use the same generic exception as the token-not-found branch so the caller
        // cannot infer that the token was valid but the user was deleted (anti-enumeration §2.6).
        UserAccount user = userAccountRepository.findById(resetToken.userId())
                .orElseThrow(() -> new TokenExpiredException("Invalid or expired reset token"));

        // Step 4: update password via domain method (also resets lock and failed attempts)
        String encoded = passwordEncoder.encode(command.newPassword());
        user.changePasswordHash(new PasswordHash(encoded));
        userAccountRepository.save(user);

        // Step 5: mark reset token as used (single-use enforcement)
        passwordResetTokenStore.markAsUsed(resetToken.id());

        // Step 6: revoke ALL sessions — possible compromise (security §2.4)
        refreshTokenStore.revokeAllFamiliesByUserId(user.getId(), "PASSWORD_RESET");

        log.info("Password reset consumed for userId={}", user.getId());
    }

    public record ConsumePasswordResetCommand(String token, String newPassword) {}
}

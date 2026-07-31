package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.RefreshTokenStore.RefreshTokenRecord;
import com.ecommerce.auth.application.port.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Logs out a user by revoking the refresh token's entire family (server-side logout).
 *
 * <p>Client-side-only logout is a review-blocking finding (security §2.4).
 * This use case performs the required server-side revocation.
 *
 * <p>If the token is not found (already expired or previously revoked) the logout
 * still returns successfully — idempotent logout is safe and preferred over errors
 * that would confuse the client into retrying.
 */
@Service
@Transactional
public class LogoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutUseCase.class);

    private final RefreshTokenStore refreshTokenRepository;
    private final TokenService tokenService;

    public LogoutUseCase(RefreshTokenStore refreshTokenRepository,
                         TokenService tokenService) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.tokenService = Objects.requireNonNull(tokenService);
    }

    public void execute(String rawRefreshToken) {
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        Optional<RefreshTokenRecord> maybeToken = refreshTokenRepository.findByTokenHash(tokenHash);

        if (maybeToken.isEmpty()) {
            // Token not found — already expired or revoked; logout is a no-op
            log.info("Logout: token not found (already expired/revoked) — idempotent no-op");
            return;
        }

        RefreshTokenRecord token = maybeToken.get();
        refreshTokenRepository.revokeFamilyById(token.familyId(), "LOGOUT");
        log.info("Logout: revoked familyId={}", token.familyId());
    }
}

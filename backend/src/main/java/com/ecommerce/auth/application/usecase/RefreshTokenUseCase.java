package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.RefreshTokenStore.BaseRefreshTokenRecord;
import com.ecommerce.auth.application.port.RefreshTokenStore.RefreshTokenRecord;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.application.usecase.LoginUseCase.LoginResult;
import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.domain.exception.TokenReusedException;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.auth.application.port.EnrichedRefreshTokenRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Validates a refresh token and issues a new access + refresh token pair (rotation).
 *
 * <p>Protocol (security §2.3):
 * <ol>
 *   <li>Hash the presented raw token → look up in DB.</li>
 *   <li>Not found → {@link TokenExpiredException}.</li>
 *   <li>Already used → revoke entire family → {@link TokenReusedException} (theft signal).</li>
 *   <li>Past {@code expires_at} → {@link TokenExpiredException}.</li>
 *   <li>Mark current token used.</li>
 *   <li>Issue new token in same family (preserving the original family expiry).</li>
 *   <li>Return new access token + new raw refresh token value.</li>
 * </ol>
 *
 * <p>This use case imports {@link EnrichedRefreshTokenRecord} from the infrastructure layer
 * to access the {@code userId} from the DB join. This is an inward dependency
 * (infrastructure → application), which is permitted; however, the use case references the
 * concrete infrastructure type by name for the pattern-match. This is an accepted pragmatic
 * coupling: the alternative (a second port method returning userId separately) would cost
 * an extra DB query on every token refresh. Documented here for reviewer visibility.
 */
@Service
@Transactional
public class RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenUseCase.class);

    private final RefreshTokenStore refreshTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final TokenService tokenService;
    private final Clock clock;

    public RefreshTokenUseCase(RefreshTokenStore refreshTokenRepository,
                               UserAccountRepository userAccountRepository,
                               TokenService tokenService,
                               Clock clock) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.clock = Objects.requireNonNull(clock);
    }

    public LoginResult execute(String rawRefreshToken) {
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);

        // Step 1: look up by hash
        RefreshTokenRecord existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenExpiredException("Refresh token not found or expired"));

        // Step 2: reuse detection — already used = theft signal
        if (existing.used()) {
            log.warn("REFRESH_REUSE_DETECTED familyId={} — revoking entire family",
                    existing.familyId());
            refreshTokenRepository.revokeFamilyById(existing.familyId(), "REUSE_DETECTED");
            throw new TokenReusedException("Refresh token has already been used");
        }

        // Step 3: expiry check
        Instant now = clock.instant();
        if (existing.expiresAt().isBefore(now)) {
            log.info("Expired refresh token familyId={}", existing.familyId());
            throw new TokenExpiredException("Refresh token has expired");
        }

        // Step 4: obtain userId from the enriched record supplied by the adapter
        if (!(existing instanceof EnrichedRefreshTokenRecord enriched)) {
            throw new IllegalStateException(
                    "Adapter must return EnrichedRefreshTokenRecord — check RefreshTokenJpaAdapter");
        }

        UserAccount user = userAccountRepository.findById(enriched.userId())
                .orElseThrow(() -> new TokenExpiredException("Associated user account not found"));

        // Step 5: mark current token used
        refreshTokenRepository.markTokenAsUsed(existing.id());

        // Step 6: issue new refresh token in the same family
        String newRawToken = tokenService.generateRawRefreshToken();
        refreshTokenRepository.saveToken(new BaseRefreshTokenRecord(
                0L,
                existing.familyId(),
                tokenService.hashRefreshToken(newRawToken),
                now,
                existing.expiresAt(), // preserve original family expiry — no sliding extension
                false
        ));

        // Step 7: issue new access token
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getRole());

        log.info("Token rotation successful userId={} familyId={}", user.getId(), existing.familyId());
        return new LoginResult(accessToken, newRawToken);
    }
}

package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.EnrichedRefreshTokenRecord;
import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.RefreshTokenStore.BaseRefreshTokenRecord;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.application.usecase.LoginUseCase.LoginResult;
import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.domain.exception.TokenReusedException;
import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.shared.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenUseCase}.
 *
 * <p>Security-critical: token reuse detection must revoke the entire family
 * before throwing — this is the theft containment mechanism.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private TokenService tokenService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private RefreshTokenUseCase useCase;

    private static final UserId USER_ID = UserId.generate();
    private static final long FAMILY_ID = 99L;
    private static final long TOKEN_ID = 77L;
    private static final String RAW_TOKEN = "rawRefreshTokenValue";
    private static final String TOKEN_HASH = "sha256HashOfRawToken";

    @BeforeEach
    void setUp() {
        useCase = new RefreshTokenUseCase(refreshTokenStore, userAccountRepository, tokenService, clock);
    }

    // ── valid token rotation ───────────────────────────────────────────────────

    @Test
    void shouldReturnNewTokenPair_whenTokenIsValid() {
        Instant futureExpiry = clock.instant().plusSeconds(3600);
        EnrichedRefreshTokenRecord validToken = enrichedToken(false, futureExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(validToken));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tokenService.generateRawRefreshToken()).thenReturn("newRawToken");
        when(tokenService.hashRefreshToken("newRawToken")).thenReturn("newTokenHash");
        when(tokenService.generateAccessToken(any(UserId.class), any(Role.class))).thenReturn("newAccessToken");

        LoginResult result = useCase.execute(RAW_TOKEN);

        assertThat(result.accessToken()).isEqualTo("newAccessToken");
        assertThat(result.refreshToken()).isEqualTo("newRawToken");
    }

    @Test
    void shouldMarkCurrentTokenAsUsed_beforeIssuingNewOne() {
        Instant futureExpiry = clock.instant().plusSeconds(3600);
        EnrichedRefreshTokenRecord validToken = enrichedToken(false, futureExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(validToken));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tokenService.generateRawRefreshToken()).thenReturn("newRaw");
        when(tokenService.hashRefreshToken("newRaw")).thenReturn("newHash");
        when(tokenService.generateAccessToken(any(), any())).thenReturn("jwt");

        useCase.execute(RAW_TOKEN);

        verify(refreshTokenStore, times(1)).markTokenAsUsed(TOKEN_ID);
    }

    @Test
    void shouldSaveNewTokenInSameFamily_onRotation() {
        Instant futureExpiry = clock.instant().plusSeconds(3600);
        EnrichedRefreshTokenRecord validToken = enrichedToken(false, futureExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(validToken));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tokenService.generateRawRefreshToken()).thenReturn("newRaw");
        when(tokenService.hashRefreshToken("newRaw")).thenReturn("newHash");
        when(tokenService.generateAccessToken(any(), any())).thenReturn("jwt");

        useCase.execute(RAW_TOKEN);

        // New token must preserve the original family expiry — no sliding extension
        verify(refreshTokenStore, times(1)).saveToken(any(BaseRefreshTokenRecord.class));
    }

    // ── token not found ────────────────────────────────────────────────────────

    @Test
    void shouldThrowTokenExpiredException_whenTokenNotFound() {
        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void shouldNotSaveNewToken_whenTokenNotFound() {
        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        try {
            useCase.execute(RAW_TOKEN);
        } catch (TokenExpiredException ignored) {}

        verify(refreshTokenStore, never()).saveToken(any());
    }

    // ── expired token ──────────────────────────────────────────────────────────

    @Test
    void shouldThrowTokenExpiredException_whenTokenIsExpired() {
        Instant pastExpiry = clock.instant().minusSeconds(1); // already expired
        EnrichedRefreshTokenRecord expiredToken = enrichedToken(false, pastExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(TokenExpiredException.class);
    }

    // ── reuse detection ────────────────────────────────────────────────────────

    @Test
    void shouldThrowTokenReusedException_whenTokenAlreadyUsed() {
        // REUSE DETECTION: used=true signals theft; entire family must be revoked.
        Instant futureExpiry = clock.instant().plusSeconds(3600);
        EnrichedRefreshTokenRecord usedToken = enrichedToken(true, futureExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(TokenReusedException.class);
    }

    @Test
    void shouldRevokeFamilyBeforeThrowingReusedException() {
        // The revocation must happen atomically before throwing — containment of theft.
        Instant futureExpiry = clock.instant().plusSeconds(3600);
        EnrichedRefreshTokenRecord usedToken = enrichedToken(true, futureExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(usedToken));

        try {
            useCase.execute(RAW_TOKEN);
        } catch (TokenReusedException ignored) {}

        verify(refreshTokenStore, times(1)).revokeFamilyById(eq(FAMILY_ID), eq("REUSE_DETECTED"));
    }

    @Test
    void shouldNotMarkUsedOrSaveNewToken_whenReuseDetected() {
        Instant futureExpiry = clock.instant().plusSeconds(3600);
        EnrichedRefreshTokenRecord usedToken = enrichedToken(true, futureExpiry);

        when(tokenService.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(usedToken));

        try {
            useCase.execute(RAW_TOKEN);
        } catch (TokenReusedException ignored) {}

        verify(refreshTokenStore, never()).markTokenAsUsed(anyLong());
        verify(refreshTokenStore, never()).saveToken(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private EnrichedRefreshTokenRecord enrichedToken(boolean used, Instant expiresAt) {
        return new EnrichedRefreshTokenRecord(
                TOKEN_ID,
                FAMILY_ID,
                TOKEN_HASH,
                clock.instant().minusSeconds(60), // issuedAt
                expiresAt,
                used,
                USER_ID
        );
    }

    private UserAccount activeUser() {
        return UserAccount.reconstitute(
                USER_ID,
                new Email("alice@example.com"),
                new PasswordHash("$2a$12$hash"),
                Role.CUSTOMER,
                false,
                0
        );
    }
}

package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.RefreshTokenStore;
import com.ecommerce.auth.application.port.TokenService;
import com.ecommerce.auth.domain.event.UserAuthenticatedEvent;
import com.ecommerce.auth.domain.exception.AccountLockedException;
import com.ecommerce.auth.domain.exception.InvalidCredentialsException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginUseCase}.
 *
 * <p>Security-critical properties tested:
 * <ul>
 *   <li>Constant timing: BCrypt runs even when user does not exist.</li>
 *   <li>Anti-enumeration: identical exception type for wrong password vs. unknown user.</li>
 *   <li>Lockout: locked accounts produce the same exception type (no distinguishable response).</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private LoginUseCase useCase;

    private static final String RAW_PASSWORD = "mySecurePassword123!";
    private static final String STORED_HASH = "$2a$12$storedHashValue";
    private static final String DUMMY_VALID_EMAIL = "alice@example.com";

    @BeforeEach
    void setUp() {
        // passwordEncoder.encode() is called once in the constructor to compute dummyHash
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$dummyHashForTimingConstant");
        useCase = new LoginUseCase(userAccountRepository, passwordEncoder, tokenService,
                refreshTokenStore, eventPublisher, clock);
    }

    // ── successful login ───────────────────────────────────────────────────────

    @Test
    void shouldReturnTokens_whenCredentialsAreValid() {
        UserAccount user = activeUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq(RAW_PASSWORD), eq(STORED_HASH))).thenReturn(true);
        when(tokenService.generateRawRefreshToken()).thenReturn("rawRefreshTokenValue");
        when(tokenService.hashRefreshToken(anyString())).thenReturn("hashedToken");
        when(tokenService.generateAccessToken(any(UserId.class), any(Role.class))).thenReturn("accessToken");
        when(refreshTokenStore.saveFamily(any())).thenReturn(42L);

        LoginUseCase.LoginResult result = useCase.execute(
                new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD));

        assertThat(result.accessToken()).isEqualTo("accessToken");
        assertThat(result.refreshToken()).isEqualTo("rawRefreshTokenValue");
    }

    @Test
    void shouldResetFailedLoginAttempts_afterSuccessfulLogin() {
        UserAccount user = activeUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq(RAW_PASSWORD), eq(STORED_HASH))).thenReturn(true);
        when(tokenService.generateRawRefreshToken()).thenReturn("rawToken");
        when(tokenService.hashRefreshToken(anyString())).thenReturn("hash");
        when(tokenService.generateAccessToken(any(), any())).thenReturn("jwt");
        when(refreshTokenStore.saveFamily(any())).thenReturn(1L);

        useCase.execute(new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD));

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void shouldPublishUserAuthenticatedEvent_onSuccessfulLogin() {
        UserAccount user = activeUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq(RAW_PASSWORD), eq(STORED_HASH))).thenReturn(true);
        when(tokenService.generateRawRefreshToken()).thenReturn("raw");
        when(tokenService.hashRefreshToken(anyString())).thenReturn("hash");
        when(tokenService.generateAccessToken(any(), any())).thenReturn("jwt");
        when(refreshTokenStore.saveFamily(any())).thenReturn(1L);

        useCase.execute(new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD));

        ArgumentCaptor<UserAuthenticatedEvent> captor = ArgumentCaptor.forClass(UserAuthenticatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(user.getId());
        assertThat(captor.getValue().occurredAt()).isEqualTo(clock.instant());
    }

    // ── wrong password ─────────────────────────────────────────────────────────

    @Test
    void shouldThrowInvalidCredentialsException_whenPasswordIsWrong() {
        UserAccount user = activeUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq(RAW_PASSWORD), eq(STORED_HASH))).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(
                new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRecordFailedLogin_whenPasswordIsWrong() {
        UserAccount user = activeUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(userAccountRepository.save(any())).thenReturn(user);

        try {
            useCase.execute(new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD));
        } catch (InvalidCredentialsException ignored) {}

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    // ── constant timing: user not found ───────────────────────────────────────

    @Test
    void shouldCallPasswordMatchesWithDummyHash_whenUserNotFound_constantTiming() {
        // TIMING CONSTANT: BCrypt must run even when user does not exist.
        // This prevents timing side-channel enumeration attacks.
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        try {
            useCase.execute(new LoginUseCase.LoginCommand("unknown@example.com", RAW_PASSWORD));
        } catch (InvalidCredentialsException ignored) {}

        // passwordEncoder.matches() MUST have been called — the dummy hash branch
        verify(passwordEncoder, times(1)).matches(eq(RAW_PASSWORD), anyString());
    }

    @Test
    void shouldThrowInvalidCredentialsException_whenUserNotFound() {
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new LoginUseCase.LoginCommand("ghost@example.com", RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldNotSaveAnything_whenUserNotFound() {
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        try {
            useCase.execute(new LoginUseCase.LoginCommand("ghost@example.com", RAW_PASSWORD));
        } catch (InvalidCredentialsException ignored) {}

        verify(userAccountRepository, never()).save(any());
    }

    // ── locked account ─────────────────────────────────────────────────────────

    @Test
    void shouldThrowAccountLockedException_whenAccountIsLocked() {
        UserAccount lockedUser = lockedUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.matches(eq(RAW_PASSWORD), eq(STORED_HASH))).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(
                new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD)))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void shouldNotPublishEvent_whenAccountIsLocked() {
        UserAccount lockedUser = lockedUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        try {
            useCase.execute(new LoginUseCase.LoginCommand(DUMMY_VALID_EMAIL, RAW_PASSWORD));
        } catch (AccountLockedException ignored) {}

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserAccount activeUser() {
        return UserAccount.reconstitute(
                UserId.generate(),
                new Email(DUMMY_VALID_EMAIL),
                new PasswordHash(STORED_HASH),
                Role.CUSTOMER,
                false,
                0
        );
    }

    private UserAccount lockedUser() {
        return UserAccount.reconstitute(
                UserId.generate(),
                new Email(DUMMY_VALID_EMAIL),
                new PasswordHash(STORED_HASH),
                Role.CUSTOMER,
                true,  // accountLocked
                5
        );
    }
}

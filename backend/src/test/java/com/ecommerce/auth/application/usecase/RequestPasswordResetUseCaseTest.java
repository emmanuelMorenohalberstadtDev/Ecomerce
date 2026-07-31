package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.port.PasswordResetTokenStore;
import com.ecommerce.auth.application.port.TokenService;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequestPasswordResetUseCase}.
 *
 * <p>Critical security property (anti-enumeration §2.6): the use case returns void
 * in BOTH the "email found" and "email not found" branches. The caller (controller)
 * always responds 202 Accepted — callers cannot distinguish the two outcomes.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RequestPasswordResetUseCaseTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;

    @Mock
    private TokenService tokenService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private RequestPasswordResetUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestPasswordResetUseCase(
                userAccountRepository, passwordResetTokenStore, tokenService, clock);
    }

    // ── anti-enumeration: both branches return silently ────────────────────────

    @Test
    void shouldReturnSilently_whenEmailExists_antiEnumeration() {
        UserAccount user = existingUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(tokenService.generateRawRefreshToken()).thenReturn("rawToken");
        when(tokenService.hashRefreshToken(anyString())).thenReturn("tokenHash");

        // Must NOT throw — void return, identical to the not-found branch
        useCase.execute(new RequestPasswordResetUseCase.RequestPasswordResetCommand("alice@example.com"));
    }

    @Test
    void shouldReturnSilently_whenEmailDoesNotExist_antiEnumeration() {
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        // Must NOT throw — identical observable response to the "email found" branch
        useCase.execute(new RequestPasswordResetUseCase.RequestPasswordResetCommand("ghost@example.com"));
    }

    // ── token is persisted when email exists ──────────────────────────────────

    @Test
    void shouldSavePasswordResetToken_whenEmailExists() {
        UserAccount user = existingUser();
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));
        when(tokenService.generateRawRefreshToken()).thenReturn("rawToken");
        when(tokenService.hashRefreshToken("rawToken")).thenReturn("tokenHash");

        useCase.execute(new RequestPasswordResetUseCase.RequestPasswordResetCommand("alice@example.com"));

        ArgumentCaptor<PasswordResetTokenStore.PasswordResetTokenRecord> captor =
                ArgumentCaptor.forClass(PasswordResetTokenStore.PasswordResetTokenRecord.class);
        verify(passwordResetTokenStore, times(1)).save(captor.capture());
        PasswordResetTokenStore.PasswordResetTokenRecord saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(user.getId());
        assertThat(saved.tokenHash()).isEqualTo("tokenHash");
        assertThat(saved.used()).isFalse();
        // TTL should be 1 hour from now per security §2.6
        assertThat(saved.expiresAt()).isEqualTo(clock.instant().plus(RequestPasswordResetUseCase.TOKEN_TTL));
    }

    @Test
    void shouldNotSaveToken_whenEmailDoesNotExist() {
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        useCase.execute(new RequestPasswordResetUseCase.RequestPasswordResetCommand("ghost@example.com"));

        verify(passwordResetTokenStore, never()).save(any());
    }

    @Test
    void shouldNotGenerateToken_whenEmailDoesNotExist() {
        when(userAccountRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        useCase.execute(new RequestPasswordResetUseCase.RequestPasswordResetCommand("ghost@example.com"));

        verify(tokenService, never()).generateRawRefreshToken();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserAccount existingUser() {
        return UserAccount.reconstitute(
                UserId.generate(),
                new Email("alice@example.com"),
                new PasswordHash("$2a$12$hash"),
                Role.CUSTOMER,
                false,
                0
        );
    }
}

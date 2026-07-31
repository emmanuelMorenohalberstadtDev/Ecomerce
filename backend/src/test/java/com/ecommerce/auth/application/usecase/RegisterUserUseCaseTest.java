package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.domain.event.UserRegisteredEvent;
import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegisterUserUseCase}.
 *
 * <p>Mocks: {@link UserAccountRepository}, {@link PasswordEncoder},
 * {@link ApplicationEventPublisher}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(userAccountRepository, passwordEncoder, eventPublisher, clock);
    }

    // ── successful registration ────────────────────────────────────────────────

    @Test
    void shouldSaveUser_whenEmailIsNew() {
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedHash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterUserUseCase.RegisterCommand("alice@example.com", "securePassword123!"));

        verify(userAccountRepository, times(1)).save(any(UserAccount.class));
    }

    @Test
    void shouldPublishUserRegisteredEvent_whenRegistrationIsSuccessful() {
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedHash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterUserUseCase.RegisterCommand("alice@example.com", "securePassword123!"));

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        UserRegisteredEvent published = eventCaptor.getValue();
        assertThat(published.email().value()).isEqualTo("alice@example.com");
        assertThat(published.occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldEncodePasswordBeforeSaving_neverSaveRawPassword() {
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(passwordEncoder.encode("myRawPassword")).thenReturn("$2a$12$encodedBcryptHash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterUserUseCase.RegisterCommand("alice@example.com", "myRawPassword"));

        ArgumentCaptor<UserAccount> savedCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedCaptor.capture());
        UserAccount saved = savedCaptor.getValue();
        // The stored hash must be the encoded value, never the raw password
        assertThat(saved.getPasswordHash().value()).isEqualTo("$2a$12$encodedBcryptHash");
        assertThat(saved.getPasswordHash().value()).doesNotContain("myRawPassword");
    }

    @Test
    void shouldCreateUserWithCustomerRole_onNewRegistration() {
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedHash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterUserUseCase.RegisterCommand("alice@example.com", "securePassword123!"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    // ── anti-enumeration: duplicate email ─────────────────────────────────────

    @Test
    void shouldReturnSilently_whenEmailAlreadyRegistered_antiEnumeration() {
        // ANTI-ENUMERATION: duplicate email must produce the exact same observable
        // outcome as a new email — no exception, no different HTTP status, nothing.
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(true);

        // Must NOT throw any exception
        useCase.execute(new RegisterUserUseCase.RegisterCommand("existing@example.com", "password123!"));

        // No save — user was already registered
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldNotPublishEvent_whenEmailAlreadyRegistered() {
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(true);

        useCase.execute(new RegisterUserUseCase.RegisterCommand("existing@example.com", "password123!"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotEncodePassword_whenEmailAlreadyRegistered() {
        when(userAccountRepository.existsByEmail(any(Email.class))).thenReturn(true);

        useCase.execute(new RegisterUserUseCase.RegisterCommand("existing@example.com", "password123!"));

        verify(passwordEncoder, never()).encode(anyString());
    }
}

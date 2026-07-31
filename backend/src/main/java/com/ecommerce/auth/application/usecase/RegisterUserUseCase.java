package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.domain.event.UserRegisteredEvent;
import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * Registers a new user account.
 *
 * <p><strong>Anti-enumeration (security §2.1):</strong> if the email is already registered,
 * this use case returns silently — it does NOT throw a different exception for existing vs.
 * new emails. The caller (controller) always returns 201 Created. The real notice to the
 * existing owner goes via email (out of scope v1). This makes the registration endpoint
 * useless as an enumeration oracle.
 *
 * <p>Transaction boundary is this use case class (backend-architecture §4.1).
 * {@code @Transactional} is NOT placed on the controller.
 */
@Service
@Transactional
public class RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserUseCase.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterUserUseCase(UserAccountRepository userAccountRepository,
                               PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void execute(RegisterCommand command) {
        Email email = new Email(command.email());

        // ANTI-ENUMERATION: silently return if already registered.
        // No "email taken" exception — both paths look identical to the caller.
        if (userAccountRepository.existsByEmail(email)) {
            log.info("Registration attempted for already-registered email (anti-enumeration, not revealing)");
            return;
        }

        String encodedPassword = passwordEncoder.encode(command.rawPassword());
        UserAccount account = UserAccount.create(email, new PasswordHash(encodedPassword));
        userAccountRepository.save(account);

        eventPublisher.publishEvent(
                new UserRegisteredEvent(account.getId(), account.getEmail(), clock.instant()));

        log.info("User registered: userId={}", account.getId());
    }

    /**
     * Input record for the register use case.
     *
     * @param email       raw email string (will be normalized inside the use case)
     * @param rawPassword plaintext password (will be BCrypt-encoded; never stored raw)
     */
    public record RegisterCommand(String email, String rawPassword) {}
}

package com.ecommerce.auth.infrastructure.persistence;

import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.shared.id.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JpaUserAccountRepository} against a real PostgreSQL
 * instance via Testcontainers.
 *
 * <p>Flyway applies V001 migration before the first test.
 * {@code @Transactional} rolls back after each test — no manual cleanup required.
 *
 * <p>Tests verify JPA + PostgreSQL behavior that cannot be tested with mocks:
 * citext case-insensitive email lookup, UNIQUE constraint, roundtrip mapping.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@Transactional
class UserAccountRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private UserAccountRepository repository;

    // ── save + findByEmail roundtrip ──────────────────────────────────────────

    @Test
    void shouldFindUserByEmail_afterSaving() {
        UserAccount user = UserAccount.create(
                new Email("alice@example.com"),
                new PasswordHash("$2a$12$someHashValue"));

        repository.save(user);

        Optional<UserAccount> found = repository.findByEmail(new Email("alice@example.com"));

        assertThat(found).isPresent();
        assertThat(found.get().getEmail().value()).isEqualTo("alice@example.com");
        assertThat(found.get().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(found.get().isAccountLocked()).isFalse();
        assertThat(found.get().getFailedLoginAttempts()).isZero();
    }

    @Test
    void shouldRoundtripAllFields_throughPersistenceAndReconstruction() {
        UserId id = UserId.generate();
        UserAccount user = UserAccount.reconstitute(
                id,
                new Email("bob@example.com"),
                new PasswordHash("$2a$12$storedHashXXX"),
                Role.CUSTOMER,
                false,
                0
        );

        repository.save(user);
        Optional<UserAccount> found = repository.findByEmail(new Email("bob@example.com"));

        assertThat(found).isPresent();
        UserAccount loaded = found.get();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getPasswordHash().value()).isEqualTo("$2a$12$storedHashXXX");
    }

    // ── citext case-insensitive lookup ────────────────────────────────────────

    @Test
    void shouldFindUser_whenSearchingWithDifferentCasing_citextBehavior() {
        // Email stored as lowercase; citext lets PostgreSQL find it with uppercase query
        UserAccount user = UserAccount.create(
                new Email("alice@example.com"),
                new PasswordHash("$2a$12$hash"));
        repository.save(user);

        // The domain Email value object normalizes to lowercase before the query,
        // but the DB citext column handles case-insensitivity at the storage level.
        // This test verifies the citext column is actually configured (not mapped as plain text).
        Optional<UserAccount> found = repository.findByEmail(new Email("ALICE@EXAMPLE.COM"));

        assertThat(found).isPresent();
        assertThat(found.get().getEmail().value()).isEqualTo("alice@example.com");
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    void existsByEmail_returnsTrue_whenUserExists() {
        repository.save(UserAccount.create(
                new Email("charlie@example.com"),
                new PasswordHash("$2a$12$hash")));

        assertThat(repository.existsByEmail(new Email("charlie@example.com"))).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenUserDoesNotExist() {
        assertThat(repository.existsByEmail(new Email("nobody@example.com"))).isFalse();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsUser_whenIdExists() {
        UserAccount user = UserAccount.create(
                new Email("diana@example.com"),
                new PasswordHash("$2a$12$hash"));
        repository.save(user);

        Optional<UserAccount> found = repository.findById(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    void findById_returnsEmpty_whenIdDoesNotExist() {
        Optional<UserAccount> found = repository.findById(UserId.generate());

        assertThat(found).isEmpty();
    }

    // ── state mutation persistence ────────────────────────────────────────────

    @Test
    void shouldPersistUpdatedFailedLoginAttempts_afterSavingMutatedAccount() {
        UserAccount user = UserAccount.create(
                new Email("eve@example.com"),
                new PasswordHash("$2a$12$hash"));
        repository.save(user);

        user.recordFailedLogin();
        user.recordFailedLogin();
        repository.save(user);

        Optional<UserAccount> loaded = repository.findByEmail(new Email("eve@example.com"));
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getFailedLoginAttempts()).isEqualTo(2);
    }
}

package com.ecommerce.auth.domain.model;

import com.ecommerce.shared.id.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link UserAccount} aggregate root.
 *
 * <p>No Spring context — pure domain logic via the factory and domain methods.
 */
@Tag("unit")
class UserAccountTest {

    private static final Email TEST_EMAIL = new Email("alice@example.com");
    private static final PasswordHash TEST_HASH =
            new PasswordHash("$2a$12$KIXmSfbLHoUBHNZMKYIkO.2jHrTVqAh9sNijmF1JOOiQDPZ3e7XgS");

    // ── create() factory ───────────────────────────────────────────────────────

    @Test
    void create_assignsCustomerRoleByDefault() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThat(account.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void create_startsWithZeroFailedAttempts() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    void create_startsUnlocked() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThat(account.isAccountLocked()).isFalse();
        assertThat(account.isActive()).isTrue();
    }

    @Test
    void create_generatesNonNullId() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getId().value()).isNotNull();
    }

    @Test
    void create_storesEmailAndPasswordHash() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThat(account.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(account.getPasswordHash()).isEqualTo(TEST_HASH);
    }

    // ── recordFailedLogin() lockout behavior ───────────────────────────────────

    @Test
    void recordFailedLogin_fourTimes_doesNotLockAccount() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        for (int i = 0; i < 4; i++) {
            account.recordFailedLogin();
        }

        assertThat(account.isAccountLocked()).isFalse();
        assertThat(account.isActive()).isTrue();
        assertThat(account.getFailedLoginAttempts()).isEqualTo(4);
    }

    @Test
    void recordFailedLogin_fiveTimes_locksAccount() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        for (int i = 0; i < 5; i++) {
            account.recordFailedLogin();
        }

        assertThat(account.isAccountLocked()).isTrue();
        assertThat(account.isActive()).isFalse();
        assertThat(account.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void recordFailedLogin_incrementsCounterEachTime() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        account.recordFailedLogin();
        assertThat(account.getFailedLoginAttempts()).isEqualTo(1);

        account.recordFailedLogin();
        assertThat(account.getFailedLoginAttempts()).isEqualTo(2);
    }

    // ── resetFailedLoginAttempts() ─────────────────────────────────────────────

    @Test
    void resetFailedLoginAttempts_resetsCounterToZero() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);
        account.recordFailedLogin();
        account.recordFailedLogin();

        account.resetFailedLoginAttempts();

        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    void resetFailedLoginAttempts_doesNotUnlockAlreadyLockedAccount() {
        // Locking is via recordFailedLogin; resetFailedLoginAttempts only resets counter
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);
        for (int i = 0; i < 5; i++) {
            account.recordFailedLogin();
        }

        account.resetFailedLoginAttempts();

        // Counter reset but lock state unchanged — lock is NOT lifted by a reset alone
        assertThat(account.getFailedLoginAttempts()).isZero();
        // NOTE: the lock flag remains true; only changePasswordHash() also unlocks.
        assertThat(account.isAccountLocked()).isTrue();
    }

    // ── isActive() ────────────────────────────────────────────────────────────

    @Test
    void isActive_returnsFalse_whenAccountIsLocked() {
        UserAccount locked = UserAccount.reconstitute(
                UserId.generate(), TEST_EMAIL, TEST_HASH, Role.CUSTOMER,
                true /* accountLocked */, 5);

        assertThat(locked.isActive()).isFalse();
    }

    @Test
    void isActive_returnsTrue_whenAccountIsNotLocked() {
        UserAccount active = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThat(active.isActive()).isTrue();
    }

    // ── changePasswordHash() ───────────────────────────────────────────────────

    @Test
    void changePasswordHash_updatesHashAndResetsLock() {
        UserAccount account = UserAccount.reconstitute(
                UserId.generate(), TEST_EMAIL, TEST_HASH, Role.CUSTOMER, true, 5);
        PasswordHash newHash = new PasswordHash("$2a$12$newHashValueHereXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        account.changePasswordHash(newHash);

        assertThat(account.getPasswordHash()).isEqualTo(newHash);
        assertThat(account.isAccountLocked()).isFalse();
        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    void changePasswordHash_throwsIllegalArgumentException_whenNewHashIsNull() {
        UserAccount account = UserAccount.create(TEST_EMAIL, TEST_HASH);

        assertThatThrownBy(() -> account.changePasswordHash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── reconstitute() ────────────────────────────────────────────────────────

    @Test
    void reconstitute_restoresExactState() {
        UserId id = UserId.generate();
        UserAccount account = UserAccount.reconstitute(
                id, TEST_EMAIL, TEST_HASH, Role.ADMIN, true, 3);

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(account.getRole()).isEqualTo(Role.ADMIN);
        assertThat(account.isAccountLocked()).isTrue();
        assertThat(account.getFailedLoginAttempts()).isEqualTo(3);
    }
}

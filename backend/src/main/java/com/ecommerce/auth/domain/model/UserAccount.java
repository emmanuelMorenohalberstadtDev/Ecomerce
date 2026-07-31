package com.ecommerce.auth.domain.model;

import com.ecommerce.shared.id.UserId;

/**
 * Aggregate root for the auth bounded context.
 *
 * <p>Models a registered user's identity and credential state. Business rules:
 * <ul>
 *   <li>Account locks after {@value #MAX_FAILED_ATTEMPTS} consecutive failed logins.</li>
 *   <li>A locked account is inactive regardless of credential correctness.</li>
 *   <li>Successful authentication resets the failed-login counter.</li>
 * </ul>
 *
 * <p>Created via {@link #create} factory; reconstituted from persistence via
 * {@link #reconstitute}. No public setters — state transitions happen through
 * named domain methods that express intent.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class UserAccount {

    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserId id;
    private Email email;
    private PasswordHash passwordHash;
    private Role role;
    private boolean accountLocked;
    private int failedLoginAttempts;

    private UserAccount(UserId id, Email email, PasswordHash passwordHash,
                        Role role, boolean accountLocked, int failedLoginAttempts) {
        if (id == null) throw new IllegalArgumentException("UserId must not be null");
        if (email == null) throw new IllegalArgumentException("Email must not be null");
        if (passwordHash == null) throw new IllegalArgumentException("PasswordHash must not be null");
        if (role == null) throw new IllegalArgumentException("Role must not be null");
        if (failedLoginAttempts < 0) throw new IllegalArgumentException("failedLoginAttempts must be >= 0");

        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountLocked = accountLocked;
        this.failedLoginAttempts = failedLoginAttempts;
    }

    /**
     * Factory for new user registration. Assigns CUSTOMER role and starts with
     * zero failed attempts and unlocked state.
     */
    public static UserAccount create(Email email, PasswordHash passwordHash) {
        return new UserAccount(UserId.generate(), email, passwordHash, Role.CUSTOMER, false, 0);
    }

    /**
     * Factory for reconstitution from persistence. Restores exact state without
     * re-running creation business rules.
     */
    public static UserAccount reconstitute(UserId id, Email email, PasswordHash passwordHash,
                                           Role role, boolean accountLocked, int failedLoginAttempts) {
        return new UserAccount(id, email, passwordHash, role, accountLocked, failedLoginAttempts);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Records one failed login attempt. Locks the account when the threshold is reached.
     */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.accountLocked = true;
        }
    }

    /**
     * Resets the failed-login counter after a successful authentication.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
    }

    /**
     * Updates the stored password hash (used by ConsumePasswordResetUseCase).
     */
    public void changePasswordHash(PasswordHash newHash) {
        if (newHash == null) throw new IllegalArgumentException("PasswordHash must not be null");
        this.passwordHash = newHash;
        // Resetting failed attempts on password change is a security courtesy
        this.failedLoginAttempts = 0;
        this.accountLocked = false;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the account is not locked. */
    public boolean isActive() {
        return !accountLocked;
    }

    /** Returns {@code true} when the role is ADMIN. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public UserId getId() { return id; }

    public Email getEmail() { return email; }

    public PasswordHash getPasswordHash() { return passwordHash; }

    public Role getRole() { return role; }

    public boolean isAccountLocked() { return accountLocked; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
}

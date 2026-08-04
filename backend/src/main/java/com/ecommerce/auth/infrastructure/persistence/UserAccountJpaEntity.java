package com.ecommerce.auth.infrastructure.persistence;

import com.ecommerce.auth.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code users} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7
 *       before INSERT (ADR-0002). No DB default exists; omitting id is a loud failure.</li>
 *   <li>{@code updated_at} is {@code insertable=false, updatable=false} because it is
 *       managed by the DB trigger {@code trg_users_updated_at}. JPA never writes it.</li>
 *   <li>{@code passwordHash} field is purposely named with a comment warning — Lombok's
 *       {@code @ToString} is not used here (no Lombok on this entity) so there is no
 *       risk of accidentally logging it, but the adapter mapper must never include it
 *       in log output.</li>
 *   <li>The {@code citext} column type is not natively supported by Hibernate's
 *       PostgreSQL dialect; we map it as {@code text} from the JPA side — the DB
 *       column definition already specifies citext and handles case-insensitivity.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_stay_in_infrastructure}.
 */
@Entity
@Table(name = "users")
public class UserAccountJpaEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    @JdbcType(CitextJdbcType.class)
    private String email;

    /**
     * BCrypt hash. Never include in logs or toString output.
     * The domain's {@link com.ecommerce.auth.domain.model.PasswordHash#toString()} returns
     * "[PROTECTED]"; the JPA entity has no such protection — be careful at mapping time.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked;

    @Column(name = "failed_login_attempts", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int failedLoginAttempts;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Managed by DB trigger {@code trg_users_updated_at}.
     * JPA never inserts or updates this column.
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // JPA requires a no-arg constructor; package-private to discourage direct use
    // -------------------------------------------------------------------------
    UserAccountJpaEntity() {}

    public UserAccountJpaEntity(UUID id, String email, String passwordHash, Role role,
                                boolean accountLocked, int failedLoginAttempts, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountLocked = accountLocked;
        this.failedLoginAttempts = failedLoginAttempts;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isAccountLocked() { return accountLocked; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters for mutable fields (needed by JPA and the adapter's update path)
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setAccountLocked(boolean accountLocked) { this.accountLocked = accountLocked; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
}

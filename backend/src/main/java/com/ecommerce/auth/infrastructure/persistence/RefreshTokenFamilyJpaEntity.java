package com.ecommerce.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code refresh_token_families} table.
 *
 * <p>PK is {@code bigint GENERATED ALWAYS AS IDENTITY} — the DB assigns it on INSERT.
 * JPA's {@code GenerationType.IDENTITY} uses a post-INSERT fetch to read it back.
 */
@Entity
@Table(name = "refresh_token_families")
public class RefreshTokenFamilyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    RefreshTokenFamilyJpaEntity() {}

    public RefreshTokenFamilyJpaEntity(UUID userId, Instant createdAt) {
        this.userId = userId;
        this.revoked = false;
        this.createdAt = createdAt;
        this.lastUsedAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public boolean isRevoked() { return revoked; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }

    public void revoke(Instant at, String reason) {
        this.revoked = true;
        this.revokedAt = at;
        this.revokedReason = reason;
    }

    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}

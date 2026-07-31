package com.ecommerce.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity mapping the {@code refresh_tokens} table.
 *
 * <p>One row per rotation step within a {@link RefreshTokenFamilyJpaEntity}.
 * The raw token value is NEVER stored — only the SHA-256 hex hash (security §2.3).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private Long familyId;

    /**
     * SHA-256 hex digest of the raw opaque token delivered to the client.
     * Never the plaintext value — the client presents the raw token, we hash and compare.
     */
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private Instant usedAt;

    RefreshTokenJpaEntity() {}

    public RefreshTokenJpaEntity(Long familyId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.used = false;
    }

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public Instant getUsedAt() { return usedAt; }

    public void markUsed(Instant at) {
        this.used = true;
        this.usedAt = at;
    }
}

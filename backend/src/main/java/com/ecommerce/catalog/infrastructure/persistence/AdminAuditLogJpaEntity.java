package com.ecommerce.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the shared, cross-context {@code admin_audit_log} table (created in
 * {@code V001__create_auth_schema.sql} §8 — not owned by catalog, but written to directly by
 * it per that migration's own comment: "The catalog application layer will INSERT into it
 * directly (backend-lead concern)").
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} uses {@code GenerationType.IDENTITY}, matching the column's
 *       {@code GENERATED ALWAYS AS IDENTITY} definition.</li>
 *   <li>{@code details} maps the {@code jsonb} column via Hibernate 6's built-in
 *       {@code @JdbcTypeCode(SqlTypes.JSON)} support (no extra dependency — approved stack
 *       already ships a Hibernate version with this feature) rather than hand-rolling a
 *       {@code String}-plus-manual-{@code ObjectMapper} conversion.</li>
 *   <li>{@code ip_address} (the DB's {@code inet} column) is deliberately NOT mapped here —
 *       left null on every insert in v1. Capturing the caller's IP would require reaching into
 *       {@code HttpServletRequest}/{@code RequestContextHolder} from this adapter and correctly
 *       round-tripping Hibernate against the Postgres {@code inet} type, neither of which could
 *       be verified against a real database in this environment (no Docker available to run the
 *       Testcontainers suite). The column is nullable, so omitting it is valid, not a broken
 *       insert — flagged as a follow-up in the catalog handoff rather than shipped unverified.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Column(name = "action_type", nullable = false, updatable = false)
    private String actionType;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> details;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;

    AdminAuditLogJpaEntity() {}

    public AdminAuditLogJpaEntity(UUID actorId, String actorEmail, String actionType,
                                  String entityType, String entityId, Map<String, Object> details,
                                  Instant occurredAt) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getActionType() { return actionType; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}

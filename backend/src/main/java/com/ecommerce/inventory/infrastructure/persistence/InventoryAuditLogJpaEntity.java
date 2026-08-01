package com.ecommerce.inventory.infrastructure.persistence;

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
 * {@code V001__create_auth_schema.sql} §8) for writes originating from the inventory context.
 *
 * <p><strong>Deliberate duplicate of catalog's {@code AdminAuditLogJpaEntity}</strong>, not a
 * shared import — see {@code inventory.application.port.AuditLogPort}'s class javadoc for why.
 * Field-for-field identical mapping; independently maintained per the feature brief.
 *
 * <p>{@code ip_address} is not mapped/populated in v1, same rationale and same known gap as
 * catalog's entity (nullable column; capturing it needs {@code HttpServletRequest} plumbing not
 * verified against a real database in this environment).
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "admin_audit_log")
public class InventoryAuditLogJpaEntity {

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

    InventoryAuditLogJpaEntity() {}

    public InventoryAuditLogJpaEntity(UUID actorId, String actorEmail, String actionType,
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

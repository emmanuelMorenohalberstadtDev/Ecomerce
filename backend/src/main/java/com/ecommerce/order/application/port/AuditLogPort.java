package com.ecommerce.order.application.port;

import java.util.Map;

/**
 * Outbound port for writing to the shared, cross-context {@code admin_audit_log} table
 * (security-architecture.md §6c).
 *
 * <p><strong>Deliberate duplication of inventory's/catalog's {@code AuditLogPort}, not a shared
 * import:</strong> a cross import would violate ADR-0001 rule 3 (contexts communicate only through
 * a target context's own {@code application.port}/{@code domain.event} packages). Per ADR-0004's
 * "sanity checks confirmed" note, order gets its own {@code AuditLogPort}/{@code CurrentActorPort}/
 * {@code OrderAuditAction}, duplicated exactly like inventory's and catalog's — a repeated,
 * settled pattern.
 *
 * <p><strong>Insert-only contract (security §6c requirement 1):</strong> exactly one method — no
 * update, no delete, no read.
 *
 * <p><strong>Transaction boundary:</strong> the implementing adapter must NOT open its own
 * transaction — it participates in whatever transaction the calling use case is running (default
 * Spring propagation, {@code REQUIRED}), so "an action without its audit row must not commit"
 * (security §6c requirement 3) holds automatically.
 */
public interface AuditLogPort {

    /**
     * Records one admin action. Must be called from within the same transaction as the audited
     * mutation.
     *
     * @param action     the action type (maps to {@code admin_audit_log.action_type})
     * @param entityType the target resource type, e.g. {@code "Order"}
     * @param entityId   string form of the target resource id
     * @param details    before/after state or other action-specific context, already scrubbed of
     *                   credentials/secrets by the caller (security §2.8) — serialized to the
     *                   {@code jsonb} {@code details} column
     */
    void record(OrderAuditAction action, String entityType, String entityId, Map<String, Object> details);
}

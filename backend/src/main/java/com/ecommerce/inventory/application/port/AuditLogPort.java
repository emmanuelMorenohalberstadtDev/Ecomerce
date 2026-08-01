package com.ecommerce.inventory.application.port;

import java.util.Map;

/**
 * Outbound port for writing to the shared, cross-context {@code admin_audit_log} table
 * (security-architecture.md §6c).
 *
 * <p><strong>Deliberate duplication of catalog's {@code AuditLogPort}, not a shared import:</strong>
 * catalog's {@code catalog.application.port.AuditLogPort} already does exactly this, but a cross
 * import here would violate ADR-0001 rule 3 (contexts communicate only through a target
 * context's own {@code application.port}/{@code domain.event} packages — and even then, "port"
 * means inventory's own port, not reaching into catalog's). Per the inventory feature brief this
 * is deliberately re-declared, independently, in this context's own {@code application.port}
 * package — exactly the promotion candidate catalog's {@code AuditLogPort} javadoc already
 * flagged ("If a second context ... needs the same mechanism, this port ... is a strong candidate
 * to promote out of catalog into a shared location"). That promotion is an architecture decision,
 * not made here; this class is the second data point for it.
 *
 * <p><strong>Deliberately NOT named {@code *Repository}</strong>: ArchUnit rule
 * {@code repository_interfaces_live_in_domain} forces every {@code *Repository} interface into
 * {@code ..domain..}, but {@code AdminAuditEntry} belongs to auth/user (domain-model.md §2), not
 * to inventory — writing to it is a cross-cutting infrastructure concern this context's use cases
 * depend on, not an inventory aggregate. Living in {@code application.port} sidesteps that rule
 * the same deliberate way catalog's identically-shaped port does.
 *
 * <p><strong>Insert-only contract (security §6c requirement 1):</strong> exactly one method — no
 * update, no delete, no read.
 *
 * <p><strong>Transaction boundary:</strong> the implementing adapter must NOT open its own
 * transaction — it participates in whatever transaction the calling use case is running (default
 * Spring propagation, {@code REQUIRED}), so "an action without its audit row must not commit"
 * (security §6c requirement 3) holds automatically.
 *
 * <p><strong>Actor resolution is the adapter's job, not the use case's</strong> for this specific
 * write — same rationale as catalog's port. Note that the {@code stock_movements.actor_id}
 * column, unlike this audit row, genuinely does need the actor id inside the use case itself (to
 * populate a different table), which is why inventory additionally introduces
 * {@link CurrentActorPort} rather than reaching for {@code SecurityContextHolder} from inside a
 * use case — see that port's javadoc.
 */
public interface AuditLogPort {

    /**
     * Records one admin action. Must be called from within the same transaction as the audited
     * mutation.
     *
     * @param action     the action type (maps to {@code admin_audit_log.action_type})
     * @param entityType the target resource type, e.g. {@code "StockItem"}
     * @param entityId   string form of the target resource id
     * @param details    before/after state or other action-specific context, already scrubbed of
     *                   credentials/secrets by the caller (security §2.8) — serialized to the
     *                   {@code jsonb} {@code details} column
     */
    void record(InventoryAuditAction action, String entityType, String entityId,
               Map<String, Object> details);
}

package com.ecommerce.catalog.application.port;

import java.util.Map;

/**
 * Outbound port for writing to the shared, cross-context {@code admin_audit_log} table
 * (security-architecture.md §6c). First-of-its-kind port in the codebase — auth has no
 * equivalent yet (see the class javadoc note below for the promotion question this raises).
 *
 * <p><strong>Deliberately NOT named {@code *Repository}</strong>: ArchUnit rule
 * {@code repository_interfaces_live_in_domain} forces every {@code *Repository} interface into
 * {@code ..domain..}, but this is not a catalog domain repository — {@code AdminAuditEntry} is
 * modeled in {@code domain-model.md} §2 as belonging to auth/user, and writing to it here is an
 * infrastructure/cross-cutting concern the catalog use cases depend on, not a catalog aggregate.
 * Living in {@code application.port} sidesteps rule 8 the same deliberate way auth's
 * {@code TokenService}/{@code RefreshTokenStore}/{@code PasswordResetTokenStore} do.
 *
 * <p><strong>Insert-only contract (security §6c requirement 1):</strong> this interface exposes
 * exactly one method — no update, no delete, no read. {@code AuditLogPortInsertOnlyTest}
 * (catalog's own test suite) asserts by reflection that no other public method is ever added.
 *
 * <p><strong>Transaction boundary:</strong> the implementing adapter must NOT open its own
 * transaction — it participates in whatever transaction the calling use case is running
 * (default Spring propagation, {@code REQUIRED}), so that "an action without its audit row must
 * not commit" (security §6c requirement 3) holds automatically: if the audit insert throws, the
 * whole use case transaction rolls back, including the audited mutation.
 *
 * <p><strong>Actor resolution is the adapter's job, not the use case's:</strong> the use case
 * only supplies the *what* (action, target entity type/id, before/after details); the adapter
 * resolves the *who* (acting user id/role) from Spring Security's {@code SecurityContextHolder}
 * itself. This keeps {@code SecurityContextHolder} — a framework type — out of the application
 * layer (ADR-0001 dependency rule: application must not depend on infrastructure) without
 * introducing a second port purely to shuttle the principal through; the read happens entirely
 * inside the infrastructure adapter that implements this interface.
 *
 * <p><strong>Open design question surfaced to software-architect:</strong> this is the first
 * audit-log port in the codebase. If a second context (e.g. order lifecycle management, stock
 * adjustments) needs the same mechanism, this port (and its JPA adapter, entity, and
 * insert-only DAO) is a strong candidate to promote out of {@code catalog} into a shared
 * location (e.g. {@code common.audit}) rather than being duplicated per context — that is an
 * architecture decision, not made here.
 */
public interface AuditLogPort {

    /**
     * Records one admin action. Must be called from within the same transaction as the audited
     * mutation.
     *
     * @param action     the action type (maps to {@code admin_audit_log.action_type})
     * @param entityType the target resource type, e.g. {@code "Product"} or {@code "Category"}
     *                   (maps to {@code admin_audit_log.entity_type})
     * @param entityId   string form of the target resource id (maps to
     *                   {@code admin_audit_log.entity_id})
     * @param details    before/after state or other action-specific context, already scrubbed
     *                   of credentials/secrets by the caller (security §2.8) — serialized to
     *                   the {@code jsonb} {@code details} column
     */
    void record(CatalogAuditAction action, String entityType, String entityId,
               Map<String, Object> details);
}

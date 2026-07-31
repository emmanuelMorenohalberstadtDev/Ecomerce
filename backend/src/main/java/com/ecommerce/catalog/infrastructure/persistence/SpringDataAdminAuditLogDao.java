package com.ecommerce.catalog.infrastructure.persistence;

/**
 * Spring Data interface for {@link AdminAuditLogJpaEntity} — insert-only by construction.
 *
 * <p>Deliberately extends the bare Spring Data {@code Repository<T, ID>} marker (not
 * {@code JpaRepository}, which bundles {@code delete}/{@code deleteById}/{@code deleteAll}) and
 * declares exactly one method: {@code save}. This is the app-level half of security
 * §6c requirement 1 ("the audit repository exposes insert and read only; no update/delete
 * methods exist") — there is deliberately no read method either, since catalog's use cases only
 * ever write to the audit log; nothing in the catalog context reads it back. Verified by
 * reflection in {@code AuditLogPortInsertOnlyTest}.
 *
 * <p>Package-private — only {@link AdminAuditLogJpaAdapter} uses it.
 */
@org.springframework.stereotype.Repository
interface SpringDataAdminAuditLogDao
        extends org.springframework.data.repository.Repository<AdminAuditLogJpaEntity, Long> {

    AdminAuditLogJpaEntity save(AdminAuditLogJpaEntity entity);
}

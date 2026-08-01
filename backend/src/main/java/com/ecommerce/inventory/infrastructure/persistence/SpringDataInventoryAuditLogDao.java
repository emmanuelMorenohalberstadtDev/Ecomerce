package com.ecommerce.inventory.infrastructure.persistence;

/**
 * Spring Data interface for {@link InventoryAuditLogJpaEntity} — insert-only by construction.
 *
 * <p>Deliberate duplicate of catalog's {@code SpringDataAdminAuditLogDao} — bare Spring Data
 * {@code Repository<T, ID>} marker (not {@code JpaRepository}), exactly one method
 * ({@code save}), no read method either (inventory's use cases only ever write to the audit log).
 *
 * <p>Package-private — only {@link InventoryAuditLogJpaAdapter} uses it.
 */
@org.springframework.stereotype.Repository
interface SpringDataInventoryAuditLogDao
        extends org.springframework.data.repository.Repository<InventoryAuditLogJpaEntity, Long> {

    InventoryAuditLogJpaEntity save(InventoryAuditLogJpaEntity entity);
}

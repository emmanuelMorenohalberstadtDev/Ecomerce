package com.ecommerce.order.infrastructure.persistence;

/**
 * Spring Data interface for {@link OrderAuditLogJpaEntity} — insert-only by construction.
 *
 * <p>Deliberate duplicate of inventory's/catalog's {@code SpringDataAdminAuditLogDao} — bare
 * Spring Data {@code Repository<T, ID>} marker (not {@code JpaRepository}), exactly one method
 * ({@code save}), no read method either (order's use cases only ever write to the audit log).
 *
 * <p>Package-private — only {@link OrderAuditLogJpaAdapter} uses it.
 */
@org.springframework.stereotype.Repository
interface SpringDataOrderAuditLogDao
        extends org.springframework.data.repository.Repository<OrderAuditLogJpaEntity, Long> {

    OrderAuditLogJpaEntity save(OrderAuditLogJpaEntity entity);
}

package com.ecommerce.inventory.infrastructure.persistence;

/**
 * Spring Data interface for {@link StockMovementJpaEntity} — insert-only by construction.
 *
 * <p>Deliberately extends the bare Spring Data {@code Repository<T, ID>} marker (not
 * {@code JpaRepository}, which bundles {@code delete}/{@code deleteById}/{@code deleteAll}) and
 * declares exactly one method: {@code save}. This is the app-level half of the DB-level
 * insert-only grant (V004 §5: "SELECT, INSERT ONLY ... enforced by the DB role itself") — mirrors
 * catalog's {@code SpringDataAdminAuditLogDao} exactly, including the deliberate absence of any
 * read method (nothing in this task's scope reads {@code stock_movements} back).
 *
 * <p>Package-private — only {@link JpaStockMovementRepository} uses it.
 */
@org.springframework.stereotype.Repository
interface SpringDataStockMovementDao
        extends org.springframework.data.repository.Repository<StockMovementJpaEntity, Long> {

    StockMovementJpaEntity save(StockMovementJpaEntity entity);
}

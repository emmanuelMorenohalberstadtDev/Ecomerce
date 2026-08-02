package com.ecommerce.order.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data interface for {@link OrderStatusHistoryJpaEntity}.
 *
 * <p>Package-private — only {@link JpaOrderRepository} uses it. Named "Dao" to avoid the ArchUnit
 * {@code *Repository}-in-domain rule. Independent of {@link SpringDataOrderDao} on purpose — see
 * {@link OrderJpaEntity}'s class javadoc for why no JPA relationship is modeled between the two.
 *
 * <p>Deliberately extends the bare Spring Data {@code Repository<T, ID>} marker (not
 * {@code JpaRepository}, which bundles {@code delete}/{@code deleteById}/{@code deleteAll}) and
 * declares only the two methods actually used by {@link JpaOrderRepository}: {@code save} and the
 * ordered lookup. Insert-only usage — never updated or deleted (rule 12; security-architecture.md
 * §6c). Mirrors inventory's {@code SpringDataStockMovementDao} and this class's sibling
 * {@link SpringDataOrderAuditLogDao}.
 */
@org.springframework.stereotype.Repository
interface SpringDataOrderStatusHistoryDao
        extends org.springframework.data.repository.Repository<OrderStatusHistoryJpaEntity, Long> {

    OrderStatusHistoryJpaEntity save(OrderStatusHistoryJpaEntity entity);

    List<OrderStatusHistoryJpaEntity> findByOrderIdOrderByOccurredAtAsc(UUID orderId);
}

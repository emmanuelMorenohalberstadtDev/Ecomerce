package com.ecommerce.order.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link OrderItemJpaEntity}.
 *
 * <p>Package-private — only {@link JpaOrderRepository} uses it. Named "Dao" to avoid the ArchUnit
 * {@code *Repository}-in-domain rule. Independent of {@link SpringDataOrderDao} on purpose — see
 * {@link OrderJpaEntity}'s class javadoc for why no JPA relationship is modeled between the two.
 */
@Repository
interface SpringDataOrderItemDao extends JpaRepository<OrderItemJpaEntity, Long> {

    List<OrderItemJpaEntity> findByOrderId(UUID orderId);
}

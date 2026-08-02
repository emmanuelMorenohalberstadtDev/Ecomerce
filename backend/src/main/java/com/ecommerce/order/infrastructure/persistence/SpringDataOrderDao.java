package com.ecommerce.order.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link OrderJpaEntity}.
 *
 * <p>Package-private — only {@link JpaOrderRepository} uses it. Named "Dao" (not "Repository") to
 * avoid the ArchUnit rule requiring every {@code *Repository} interface to live in
 * {@code ..domain..}, mirroring {@code SpringDataCheckoutSessionDao}.
 *
 * <p>{@link #findByCustomerId} returns full header rows (a heap scan, not an index-only scan of
 * {@code idx_orders_customer_placed}'s {@code INCLUDE} columns) — a deliberate simplification
 * matching {@code catalog.infrastructure.persistence.SpringDataProductDao#searchActive}'s existing
 * precedent of paginating full entities rather than a JPQL constructor projection (no such
 * projection pattern exists elsewhere in this codebase yet). {@link JpaOrderRepository} narrows
 * each row to {@link com.ecommerce.order.domain.OrderRepository.OrderSummary} before it leaves the
 * adapter, so the wider domain never sees this extra column cost — flagged as a future
 * optimization candidate, not a correctness issue.
 */
@Repository
interface SpringDataOrderDao extends JpaRepository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<OrderJpaEntity> findByReservationId(UUID reservationId);

    Page<OrderJpaEntity> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);
}

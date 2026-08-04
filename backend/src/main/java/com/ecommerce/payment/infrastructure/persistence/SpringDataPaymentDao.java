package com.ecommerce.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link PaymentJpaEntity}.
 *
 * <p>Package-private — only {@link JpaPaymentRepository} uses it. Named "Dao" (not "Repository")
 * to avoid the ArchUnit rule requiring every {@code *Repository} interface to live in
 * {@code ..domain..}, mirroring {@code SpringDataOrderDao}.
 */
@Repository
interface SpringDataPaymentDao extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByOrderIdAndCustomerId(UUID orderId, UUID customerId);

    Optional<PaymentJpaEntity> findByOrderId(UUID orderId);
}

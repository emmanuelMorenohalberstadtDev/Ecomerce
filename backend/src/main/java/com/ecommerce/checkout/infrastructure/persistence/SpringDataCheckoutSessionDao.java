package com.ecommerce.checkout.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link CheckoutSessionJpaEntity}.
 *
 * <p>Package-private — only {@link JpaCheckoutSessionRepository} uses it. Named "Dao" (not
 * "Repository") to avoid the ArchUnit rule requiring every {@code *Repository} interface to live
 * in {@code ..domain..}, mirroring {@code SpringDataCartDao}/{@code SpringDataStockReservationDao}.
 *
 * <p>{@link #findAwaitingPaymentExpiredAsOf} backs {@code ExpirePaymentWindowUseCase}'s sweep via
 * {@code idx_checkout_sessions_deadline_open}. {@link #findByCustomerIdAndIdempotencyKey} backs the
 * per-customer idempotency-key replay check via {@code uq_checkout_sessions_idempotency}.
 * {@link #findOpenByCustomerId} backs {@code StartCheckoutUseCase}'s already-open-session guard via
 * {@code idx_checkout_sessions_customer_id} (a small in-memory status filter on the per-customer
 * result set — no dedicated index needed, per security-engineer review).
 */
@Repository
interface SpringDataCheckoutSessionDao extends JpaRepository<CheckoutSessionJpaEntity, UUID> {

    Optional<CheckoutSessionJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<CheckoutSessionJpaEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    @Query("SELECT s FROM CheckoutSessionJpaEntity s WHERE s.customerId = :customerId "
            + "AND s.status IN ('PENDING_CONFIRMATION', 'AWAITING_PAYMENT')")
    List<CheckoutSessionJpaEntity> findOpenByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT s FROM CheckoutSessionJpaEntity s WHERE s.status = 'AWAITING_PAYMENT' "
            + "AND s.paymentDeadline IS NOT NULL AND s.paymentDeadline < :now")
    List<CheckoutSessionJpaEntity> findAwaitingPaymentExpiredAsOf(@Param("now") Instant now);
}

package com.ecommerce.payment.infrastructure.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data interface for {@link RefundJpaEntity}.
 *
 * <p>Package-private — only {@link JpaPaymentRepository} uses it. Independent of
 * {@link SpringDataPaymentDao} on purpose — see {@code PaymentJpaEntity}'s class javadoc.
 *
 * <p>Deliberately extends the bare Spring Data {@code Repository<T, ID>} marker, declaring only
 * {@code save} and the ordered lookup — insert-only usage (V007 §4), mirrors
 * {@link SpringDataPaymentAttemptDao}.
 */
@org.springframework.stereotype.Repository
interface SpringDataRefundDao extends Repository<RefundJpaEntity, Long> {

    RefundJpaEntity save(RefundJpaEntity entity);

    List<RefundJpaEntity> findByPaymentIdOrderByIssuedAtAsc(UUID paymentId);
}

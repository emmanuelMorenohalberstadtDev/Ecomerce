package com.ecommerce.payment.infrastructure.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data interface for {@link PaymentAttemptJpaEntity}.
 *
 * <p>Package-private — only {@link JpaPaymentRepository} uses it. Independent of
 * {@link SpringDataPaymentDao} on purpose — see {@code PaymentJpaEntity}'s class javadoc for why no
 * JPA relationship is modeled between the two.
 *
 * <p>Deliberately extends the bare Spring Data {@code Repository<T, ID>} marker (not
 * {@code JpaRepository}, which bundles {@code delete}/{@code deleteById}/{@code deleteAll}) and
 * declares only the two methods actually used: {@code save} and the ordered lookup. Insert-only
 * usage — never updated or deleted (V007 §4), mirrors
 * {@code order.infrastructure.persistence.SpringDataOrderStatusHistoryDao}.
 */
@org.springframework.stereotype.Repository
interface SpringDataPaymentAttemptDao extends Repository<PaymentAttemptJpaEntity, Long> {

    PaymentAttemptJpaEntity save(PaymentAttemptJpaEntity entity);

    List<PaymentAttemptJpaEntity> findByPaymentIdOrderByAttemptedAtAsc(UUID paymentId);
}

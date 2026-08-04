package com.ecommerce.payment.infrastructure.persistence;

import com.ecommerce.payment.domain.exception.DuplicatePaymentException;
import com.ecommerce.payment.domain.exception.PaymentConcurrentModificationException;
import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentAttempt;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.model.Refund;
import com.ecommerce.payment.domain.model.RefundReason;
import com.ecommerce.payment.domain.port.out.PaymentRepository;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.money.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link PaymentRepository} port.
 *
 * <p>Uses three independent Spring Data DAOs ({@link SpringDataPaymentDao},
 * {@link SpringDataPaymentAttemptDao}, {@link SpringDataRefundDao}) rather than one JPA entity
 * graph with mapped {@code @OneToMany} collections — see {@link PaymentJpaEntity}'s class javadoc
 * for why. {@link #create} writes the header only; {@link #appendAttempt}/{@link #appendRefund}
 * each write exactly one new child row; {@link #save} writes the header only (status/version),
 * after a {@code markCaptured}/{@code markRefunded} call.
 *
 * <p>Translates vendor exceptions into payment's own domain exceptions at the boundary (hexagonal
 * skill): a lost {@code uq_payments_order} race on {@link #create}
 * ({@link DataIntegrityViolationException}) becomes {@link DuplicatePaymentException} (409); a
 * lost optimistic-lock race on {@link #save} ({@link ObjectOptimisticLockingFailureException})
 * becomes {@link PaymentConcurrentModificationException} (409) — mirrors
 * {@code order.infrastructure.persistence.JpaOrderRepository} exactly.
 */
public class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPaymentDao springDataPaymentDao;
    private final SpringDataPaymentAttemptDao springDataPaymentAttemptDao;
    private final SpringDataRefundDao springDataRefundDao;

    public JpaPaymentRepository(SpringDataPaymentDao springDataPaymentDao,
                                SpringDataPaymentAttemptDao springDataPaymentAttemptDao,
                                SpringDataRefundDao springDataRefundDao) {
        this.springDataPaymentDao = Objects.requireNonNull(springDataPaymentDao);
        this.springDataPaymentAttemptDao = Objects.requireNonNull(springDataPaymentAttemptDao);
        this.springDataRefundDao = Objects.requireNonNull(springDataRefundDao);
    }

    @Override
    public Payment create(Payment payment) {
        PaymentJpaEntity saved;
        try {
            saved = springDataPaymentDao.saveAndFlush(toHeaderEntity(payment));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicatePaymentException(
                    "A payment already exists for order " + payment.getOrderId());
        }
        return toDomain(saved, List.of(), List.of());
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity saved;
        try {
            saved = springDataPaymentDao.save(toHeaderEntity(payment));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new PaymentConcurrentModificationException(
                    "Payment " + payment.getId() + " was modified concurrently; reload and retry", e);
        }
        // Attempts/refunds are write-once, appended individually elsewhere — the in-memory list on
        // `payment` already reflects every persisted row exactly, no re-query needed (mirrors
        // JpaOrderRepository.save reusing order.getLines()/getStatusHistory() in-memory).
        return toDomain(saved, payment.getAttempts(), payment.getRefunds());
    }

    @Override
    public void appendAttempt(PaymentId paymentId, PaymentAttempt attempt) {
        springDataPaymentAttemptDao.save(new PaymentAttemptJpaEntity(
                paymentId.value(), attempt.idempotencyKey(), attempt.outcome().name(),
                attempt.declineReason() == null ? null : attempt.declineReason().name(),
                attempt.gatewayReference(), attempt.amount().amount(), attempt.amount().currency(),
                attempt.attemptedAt()));
    }

    @Override
    public void appendRefund(PaymentId paymentId, Refund refund) {
        springDataRefundDao.save(new RefundJpaEntity(
                paymentId.value(), refund.amount().amount(), refund.amount().currency(),
                refund.reason().name(), refund.gatewayReference(), refund.issuedAt()));
    }

    @Override
    public Optional<Payment> findByOrderIdAndCustomerId(OrderId orderId, CustomerId customerId) {
        return springDataPaymentDao.findByOrderIdAndCustomerId(orderId.value(), customerId.value())
                .map(this::toDomainFull);
    }

    @Override
    public Optional<Payment> findByOrderId(OrderId orderId) {
        return springDataPaymentDao.findByOrderId(orderId.value()).map(this::toDomainFull);
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private PaymentJpaEntity toHeaderEntity(Payment payment) {
        Money amount = payment.getAmount();
        return new PaymentJpaEntity(
                payment.getId().value(),
                payment.getOrderId().value(),
                payment.getCustomerId().value(),
                payment.getStatus().name(),
                amount.amount(),
                amount.currency(),
                payment.getVersion(),
                payment.getCreatedAt());
    }

    /** Full reconstruction: header + attempts + refunds — 3 targeted queries, see class javadoc. */
    private Payment toDomainFull(PaymentJpaEntity entity) {
        List<PaymentAttempt> attempts = springDataPaymentAttemptDao
                .findByPaymentIdOrderByAttemptedAtAsc(entity.getId()).stream()
                .map(this::toAttempt)
                .toList();
        List<Refund> refunds = springDataRefundDao.findByPaymentIdOrderByIssuedAtAsc(entity.getId()).stream()
                .map(this::toRefund)
                .toList();
        return toDomain(entity, attempts, refunds);
    }

    private Payment toDomain(PaymentJpaEntity entity, List<PaymentAttempt> attempts, List<Refund> refunds) {
        Money amount = new Money(entity.getAmount(), entity.getCurrency());
        return Payment.reconstitute(
                PaymentId.of(entity.getId()),
                OrderId.of(entity.getOrderId()),
                CustomerId.of(entity.getCustomerId()),
                PaymentStatus.valueOf(entity.getStatus()),
                amount,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                attempts,
                refunds);
    }

    private PaymentAttempt toAttempt(PaymentAttemptJpaEntity e) {
        return new PaymentAttempt(
                e.getIdempotencyKey(),
                PaymentOutcome.valueOf(e.getOutcome()),
                e.getDeclineReason() == null ? null : DeclineReason.valueOf(e.getDeclineReason()),
                e.getGatewayReference(),
                new Money(e.getAmount(), e.getCurrency()),
                e.getAttemptedAt());
    }

    private Refund toRefund(RefundJpaEntity e) {
        return new Refund(
                new Money(e.getAmount(), e.getCurrency()),
                RefundReason.valueOf(e.getReason()),
                e.getGatewayReference(),
                e.getIssuedAt());
    }
}

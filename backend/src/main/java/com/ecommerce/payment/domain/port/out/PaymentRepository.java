package com.ecommerce.payment.domain.port.out;

import com.ecommerce.payment.domain.exception.DuplicatePaymentException;
import com.ecommerce.payment.domain.exception.PaymentConcurrentModificationException;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentAttempt;
import com.ecommerce.payment.domain.model.Refund;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;

import java.util.Optional;

/**
 * The single repository interface for the {@link Payment} aggregate root (domain-model.md §2
 * ownership rule: "one repository per aggregate root, nothing else"). {@code payment_attempts}/
 * {@code refunds} are owned entities inside this aggregate — persisting them is this interface's
 * implementation's job via the two dedicated append methods below, never a separate repository.
 *
 * <p>Implemented by {@code payment.infrastructure.persistence.JpaPaymentRepository}, using three
 * independent Spring Data DAOs, mirroring {@code order.infrastructure.persistence.JpaOrderRepository}'s
 * reasoning exactly (no {@code @OneToMany} collection on the JPA entity; write-once-per-row child
 * tables persisted via their own targeted DAO).
 */
public interface PaymentRepository {

    /**
     * Persists a brand-new {@code PENDING} payment — header only, no attempts/refunds yet (both
     * are always appended afterward in the same transaction, via {@link #appendAttempt}).
     *
     * @throws DuplicatePaymentException if {@code uq_payments_order} is violated — a lost race
     *         against another concurrent submission for the same order
     */
    Payment create(Payment payment);

    /**
     * Persists the header only (status/version) — call after {@code markCaptured}/
     * {@code markRefunded}. Never touches {@code payment_attempts}/{@code refunds} — both are
     * write-once, appended individually via {@link #appendAttempt}/{@link #appendRefund}.
     *
     * @throws PaymentConcurrentModificationException if the optimistic-lock version check fails
     */
    Payment save(Payment payment);

    /** Appends exactly one new row to {@code payment_attempts} (insert-only, V007 §4). */
    void appendAttempt(PaymentId paymentId, PaymentAttempt attempt);

    /** Appends exactly one new row to {@code refunds} (insert-only, V007 §4). */
    void appendRefund(PaymentId paymentId, Refund refund);

    /**
     * Ownership-scoped read (security-architecture §3.2/§3.3): empty if no payment exists for
     * {@code orderId} yet, or it exists but belongs to a different customer — both cases return
     * empty identically.
     */
    Optional<Payment> findByOrderIdAndCustomerId(OrderId orderId, CustomerId customerId);

    /**
     * System-scoped read (no ownership check) for {@code IssueRefundOnOrderCancelledUseCase} —
     * triggered by an {@code OrderCancelledEvent} listener, which has no authenticated principal to
     * scope by.
     */
    Optional<Payment> findByOrderId(OrderId orderId);
}

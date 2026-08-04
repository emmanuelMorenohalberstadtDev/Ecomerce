package com.ecommerce.payment.domain.model;

import com.ecommerce.payment.domain.exception.InvalidPaymentStateException;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for the payment bounded context (domain-model.md §1/§2 {@code Payment}; ADR-0005).
 *
 * <p>One row per order ({@code payments.order_id} is {@code NOT NULL UNIQUE},
 * {@code V007__create_payment_schema.sql}) — {@code amount} is the authoritative charge amount,
 * sourced exclusively from {@code order.application.port.OrderPaymentPort.getForPayment(...).grandTotal()}
 * at {@link #initiate} time (ADR-0005 sanity check: "never a client-supplied amount"), never
 * re-derived afterward.
 *
 * <p>Owns its {@link PaymentAttempt} and {@link Refund} children (domain-model.md §2 ownership
 * rules) and the one-way {@link PaymentStatus} transition graph:
 * {@code PENDING -> CAPTURED} ({@link #markCaptured}, on a gateway {@code APPROVED} outcome) and
 * {@code CAPTURED -> REFUNDED} ({@link #markRefunded}, on {@code OrderCancelledEvent}, async).
 * Never any other edge, never backward.
 *
 * <p><strong>References out by typed id only</strong> (domain-model.md §2): {@link #orderId} and
 * {@link #customerId} — never object references.
 *
 * <p>Created via {@link #initiate}; reconstituted from persistence via {@link #reconstitute}. No
 * public setters — state changes happen through named domain methods only.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class Payment {

    private final PaymentId id;
    private final OrderId orderId;
    private final CustomerId customerId;
    private PaymentStatus status;
    private final Money amount;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<PaymentAttempt> attempts;
    private final List<Refund> refunds;

    private Payment(PaymentId id, OrderId orderId, CustomerId customerId, PaymentStatus status, Money amount,
                    long version, Instant createdAt, Instant updatedAt, List<PaymentAttempt> attempts,
                    List<Refund> refunds) {
        if (id == null) {
            throw new IllegalArgumentException("PaymentId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId must not be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("CustomerId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("PaymentStatus must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Money amount must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (attempts == null) {
            throw new IllegalArgumentException("attempts must not be null (use an empty list)");
        }
        if (refunds == null) {
            throw new IllegalArgumentException("refunds must not be null (use an empty list)");
        }
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.amount = amount;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attempts = new ArrayList<>(attempts);
        this.refunds = new ArrayList<>(refunds);
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Creates a brand-new payment in {@link PaymentStatus#PENDING}, one per order. {@code amount}
     * must come from {@code OrderPaymentPort.getForPayment(...).grandTotal()} — never a
     * client-supplied value (ADR-0005 sanity check) — this factory does not itself verify the
     * source, that discipline lives in {@code SubmitPaymentUseCase}.
     */
    public static Payment initiate(PaymentId id, OrderId orderId, CustomerId customerId, Money amount, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return new Payment(id, orderId, customerId, PaymentStatus.PENDING, amount, 0L, now, now, List.of(), List.of());
    }

    /** Factory for reconstitution from persistence. Restores exact state without re-running creation rules. */
    public static Payment reconstitute(PaymentId id, OrderId orderId, CustomerId customerId, PaymentStatus status,
                                       Money amount, long version, Instant createdAt, Instant updatedAt,
                                       List<PaymentAttempt> attempts, List<Refund> refunds) {
        return new Payment(id, orderId, customerId, status, amount, version, createdAt, updatedAt, attempts, refunds);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Records one charge attempt against this payment (ADR-0005 item 2). Does not itself change
     * {@link #status} — a {@code DECLINED}/{@code TIMEOUT} outcome leaves the payment
     * {@code PENDING} and retryable; only {@link #markCaptured} moves the status, called
     * separately by the use case after inspecting the recorded attempt's outcome.
     *
     * @throws InvalidPaymentStateException if this payment is not currently {@code PENDING} — a
     *         defensive backstop; {@code SubmitPaymentUseCase}'s own read-time check against the
     *         order's status is the primary fast-fail guard (ADR-0005 sanity check)
     */
    public void recordAttempt(String idempotencyKey, PaymentOutcome outcome, DeclineReason declineReason,
                              String gatewayReference, Instant attemptedAt) {
        requirePending();
        this.attempts.add(new PaymentAttempt(idempotencyKey, outcome, declineReason, gatewayReference,
                this.amount, attemptedAt));
    }

    /**
     * {@code PENDING -> CAPTURED} — payment approved. Callers are responsible for driving
     * {@code order.application.port.OrderPaymentPort#markPaid} in the same transaction (ADR-0005
     * §Decision item 1) — this method only flips status, mirroring
     * {@code StockReservation#commit()}'s division of responsibility.
     *
     * @throws InvalidPaymentStateException if this payment is not currently {@code PENDING}
     */
    public void markCaptured(Instant now) {
        requirePending();
        this.status = PaymentStatus.CAPTURED;
        this.updatedAt = now;
    }

    /**
     * {@code CAPTURED -> REFUNDED} — full refund issued on order cancellation (ADR-0005 item 4).
     * Records the {@link Refund} child and flips status in one call.
     *
     * @throws InvalidPaymentStateException if this payment is not currently {@code CAPTURED}
     */
    public void markRefunded(RefundReason reason, String gatewayReference, Instant issuedAt) {
        requireCaptured();
        this.refunds.add(new Refund(this.amount, reason, gatewayReference, issuedAt));
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = issuedAt;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** True idempotent replay lookup (ADR-0005 item 2; V007's {@code uq_payment_attempts_idempotency}). */
    public Optional<PaymentAttempt> findAttemptByIdempotencyKey(String idempotencyKey) {
        return attempts.stream().filter(a -> a.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    /** The single attempt just appended by {@link #recordAttempt}. */
    public PaymentAttempt getLatestAttempt() {
        return attempts.get(attempts.size() - 1);
    }

    /** The single refund just appended by {@link #markRefunded}. */
    public Refund getLatestRefund() {
        return refunds.get(refunds.size() - 1);
    }

    /** Unmodifiable, defensively-copied view of every attempt made against this payment. */
    public List<PaymentAttempt> getAttempts() {
        return Collections.unmodifiableList(new ArrayList<>(attempts));
    }

    /** Unmodifiable, defensively-copied view of every refund issued against this payment. */
    public List<Refund> getRefunds() {
        return Collections.unmodifiableList(new ArrayList<>(refunds));
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public PaymentId getId() { return id; }

    public OrderId getOrderId() { return orderId; }

    public CustomerId getCustomerId() { return customerId; }

    public PaymentStatus getStatus() { return status; }

    public Money getAmount() { return amount; }

    public long getVersion() { return version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    "Payment " + id + " is not PENDING (status=" + status + ")");
        }
    }

    private void requireCaptured() {
        if (status != PaymentStatus.CAPTURED) {
            throw new InvalidPaymentStateException(
                    "Payment " + id + " is not CAPTURED (status=" + status + ")");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

package com.ecommerce.checkout.domain.model;

import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for the checkout bounded context (domain-model.md §1/§2 {@code CheckoutSession}).
 *
 * <p>Owns the purchase-process orchestration state only — no catalog, stock, or price data
 * (domain-model.md §1 checkout: "only process state"). Owns the one-way {@link SessionStatus}
 * transition graph: {@code PENDING_CONFIRMATION -> AWAITING_PAYMENT} ({@link #moveToAwaitingPayment})
 * or {@code AWAITING_PAYMENT -> EXPIRED} ({@link #expire}). Never any other edge, mirroring
 * {@code inventory.domain.model.StockReservation}'s {@code HELD -> COMMITTED}/{@code HELD -> RELEASED}
 * pattern exactly (ADR-0003 §Decision item 3/4; {@code V005__create_checkout_schema.sql} status
 * column comment).
 *
 * <p><strong>References out by typed id only</strong> (domain-model.md §2): {@link #customerId},
 * {@link #cartId}, and a nullable {@link #reservationId} — never object references.
 *
 * <p>{@link #recalculatedTotal} is checkout's own {@link RecalculatedTotal} value object
 * (ADR-0003 §Decision item 2) — never a pricing type. {@link #expectedTotal} is the total the
 * client/cart believed BEFORE recalculation (api-guidelines §8.5: carried only for
 * re-confirmation comparison, never as arithmetic input) — set once at {@link #start} and never
 * changed afterward; {@link #requiresReconfirmation()} compares it against the current
 * {@code recalculatedTotal}.
 *
 * <p>Created via {@link #start}; reconstituted from persistence via {@link #reconstitute}. No
 * public setters — state changes happen through named domain methods.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class CheckoutSession {

    private final CheckoutSessionId id;
    private final CustomerId customerId;
    private final CartId cartId;
    private SessionStatus status;
    private ReservationId reservationId;
    private RecalculatedTotal recalculatedTotal;
    private final Money expectedTotal;
    private Instant paymentDeadline;
    private final String idempotencyKey;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CheckoutSession(CheckoutSessionId id, CustomerId customerId, CartId cartId, SessionStatus status,
                            ReservationId reservationId, RecalculatedTotal recalculatedTotal, Money expectedTotal,
                            Instant paymentDeadline, String idempotencyKey, long version,
                            Instant createdAt, Instant updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("CheckoutSessionId must not be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("CustomerId must not be null");
        }
        if (cartId == null) {
            throw new IllegalArgumentException("CartId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("SessionStatus must not be null");
        }
        if (recalculatedTotal == null) {
            throw new IllegalArgumentException("RecalculatedTotal must not be null");
        }
        if (expectedTotal == null) {
            throw new IllegalArgumentException("expectedTotal must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        this.id = id;
        this.customerId = customerId;
        this.cartId = cartId;
        this.status = status;
        this.reservationId = reservationId;
        this.recalculatedTotal = recalculatedTotal;
        this.expectedTotal = expectedTotal;
        this.paymentDeadline = paymentDeadline;
        this.idempotencyKey = idempotencyKey;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Creates a brand-new session in {@link SessionStatus#PENDING_CONFIRMATION} — no reservation
     * exists yet (ADR-0003 {@code StartCheckoutUseCase} behavior: a reservation is only made once
     * the recalculated total is confirmed).
     */
    public static CheckoutSession start(CheckoutSessionId id, CustomerId customerId, CartId cartId,
                                        RecalculatedTotal recalculatedTotal, Money expectedTotal,
                                        String idempotencyKey, Instant now) {
        return new CheckoutSession(id, customerId, cartId, SessionStatus.PENDING_CONFIRMATION,
                null, recalculatedTotal, expectedTotal, null, idempotencyKey, 0L, now, now);
    }

    /** Factory for reconstitution from persistence. Restores exact state without re-running creation rules. */
    public static CheckoutSession reconstitute(CheckoutSessionId id, CustomerId customerId, CartId cartId,
                                               SessionStatus status, ReservationId reservationId,
                                               RecalculatedTotal recalculatedTotal, Money expectedTotal,
                                               Instant paymentDeadline, String idempotencyKey, long version,
                                               Instant createdAt, Instant updatedAt) {
        return new CheckoutSession(id, customerId, cartId, status, reservationId, recalculatedTotal,
                expectedTotal, paymentDeadline, idempotencyKey, version, createdAt, updatedAt);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * @return {@code true} if {@link #expectedTotal} disagrees with the current
     *         {@code recalculatedTotal}'s grand total — the price-re-confirmation trigger
     *         (domain-model.md §5 "Price re-confirmation"; ADR-0003).
     */
    public boolean requiresReconfirmation() {
        return !recalculatedTotal.grandTotal().equals(expectedTotal);
    }

    /**
     * Replaces the in-memory {@link #recalculatedTotal} with a freshly computed one — used by
     * {@code ConfirmRecalculatedTotalUseCase} when prices moved again between the customer's
     * re-confirmation and this call (see that use case's javadoc for the "stay in
     * PENDING_CONFIRMATION rather than loop" simplicity choice).
     *
     * @throws InvalidCheckoutSessionStateException if this session is not currently
     *         {@link SessionStatus#PENDING_CONFIRMATION}
     */
    public void refreshRecalculatedTotal(RecalculatedTotal newTotal) {
        requirePendingConfirmation();
        Objects.requireNonNull(newTotal, "newTotal must not be null");
        this.recalculatedTotal = newTotal;
    }

    /**
     * Transitions {@code PENDING_CONFIRMATION -> AWAITING_PAYMENT}: records the reservation just
     * made and starts the payment window.
     *
     * @throws InvalidCheckoutSessionStateException if this session is not currently
     *         {@link SessionStatus#PENDING_CONFIRMATION}
     */
    public void moveToAwaitingPayment(ReservationId reservationId, Instant paymentDeadline) {
        requirePendingConfirmation();
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(paymentDeadline, "paymentDeadline must not be null");
        this.status = SessionStatus.AWAITING_PAYMENT;
        this.reservationId = reservationId;
        this.paymentDeadline = paymentDeadline;
    }

    /**
     * Transitions {@code AWAITING_PAYMENT -> EXPIRED}: the payment window lapsed
     * ({@code ExpirePaymentWindowUseCase}).
     *
     * @throws InvalidCheckoutSessionStateException if this session is not currently
     *         {@link SessionStatus#AWAITING_PAYMENT}
     */
    public void expire() {
        requireAwaitingPayment();
        this.status = SessionStatus.EXPIRED;
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public CheckoutSessionId getId() { return id; }

    public CustomerId getCustomerId() { return customerId; }

    public CartId getCartId() { return cartId; }

    public SessionStatus getStatus() { return status; }

    public ReservationId getReservationId() { return reservationId; }

    public RecalculatedTotal getRecalculatedTotal() { return recalculatedTotal; }

    public Money getExpectedTotal() { return expectedTotal; }

    public Instant getPaymentDeadline() { return paymentDeadline; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public long getVersion() { return version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requirePendingConfirmation() {
        if (status != SessionStatus.PENDING_CONFIRMATION) {
            throw new InvalidCheckoutSessionStateException(
                    "CheckoutSession " + id + " is not PENDING_CONFIRMATION (status=" + status + ")");
        }
    }

    private void requireAwaitingPayment() {
        if (status != SessionStatus.AWAITING_PAYMENT) {
            throw new InvalidCheckoutSessionStateException(
                    "CheckoutSession " + id + " is not AWAITING_PAYMENT (status=" + status + ")");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CheckoutSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

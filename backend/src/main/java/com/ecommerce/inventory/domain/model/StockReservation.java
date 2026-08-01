package com.ecommerce.inventory.domain.model;

import com.ecommerce.inventory.domain.exception.InvalidReservationStateException;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for a checkout-time stock hold (domain-model.md §2/§3).
 *
 * <p>Owns its {@link ReservedLine} children (domain-model.md §2 ownership rules) and the
 * one-way {@link ReservationStatus} transition graph: {@code HELD -> COMMITTED} (payment
 * succeeds, {@link #commit()}) or {@code HELD -> RELEASED} (checkout failure/expiry, order
 * cancellation, {@link #release()}). Never any other edge, never back to {@code HELD}.
 *
 * <p><strong>References out by typed id only</strong> (domain-model.md §2 "References out"
 * column): {@link #checkoutSessionId} (a {@link CheckoutSessionId}) and, conceptually, an
 * {@code OrderId} for a committed reservation — <strong>the latter is not modelled as a field
 * here</strong> because {@code stock_reservations} has no {@code order_id} column in
 * {@code V004__create_inventory_schema.sql} (database-engineer's migration header explicitly
 * declines to add one — the checkout/order contexts don't have tables yet either). See the
 * inventory handoff report for this gap; a future migration would add the column and this class
 * would gain the field then, not before.
 *
 * <p>Created via {@link #create}; reconstituted from persistence via {@link #reconstitute}. No
 * public setters — state changes happen through named domain methods.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class StockReservation {

    private final ReservationId id;
    private final CheckoutSessionId checkoutSessionId;
    private ReservationStatus status;
    private final Expiry expiresAt;
    private final Instant createdAt;
    private final List<ReservedLine> lines;

    private StockReservation(ReservationId id, CheckoutSessionId checkoutSessionId,
                             ReservationStatus status, Expiry expiresAt, Instant createdAt,
                             List<ReservedLine> lines) {
        if (id == null) {
            throw new IllegalArgumentException("ReservationId must not be null");
        }
        if (checkoutSessionId == null) {
            throw new IllegalArgumentException("CheckoutSessionId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("ReservationStatus must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiry must not be null (use Expiry.none())");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("StockReservation must hold at least one line");
        }
        if (expiresAt.isPresent() && !expiresAt.value().isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be strictly after createdAt, got expiresAt=" + expiresAt.value()
                            + " createdAt=" + createdAt);
        }
        this.id = id;
        this.checkoutSessionId = checkoutSessionId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.lines = new ArrayList<>(lines);
    }

    /**
     * Factory for a brand-new {@code HELD} reservation. Called by {@code ReserveStockUseCase}
     * only after every line's atomic conditional decrement has already succeeded — this
     * constructor does not itself touch {@code stock_items}.
     */
    public static StockReservation create(ReservationId id, CheckoutSessionId checkoutSessionId,
                                          List<ReservedLine> lines, Expiry expiresAt, Instant createdAt) {
        return new StockReservation(id, checkoutSessionId, ReservationStatus.HELD, expiresAt, createdAt, lines);
    }

    /** Factory for reconstitution from persistence. Restores exact state without re-running creation rules. */
    public static StockReservation reconstitute(ReservationId id, CheckoutSessionId checkoutSessionId,
                                                ReservationStatus status, Expiry expiresAt, Instant createdAt,
                                                List<ReservedLine> lines) {
        return new StockReservation(id, checkoutSessionId, status, expiresAt, createdAt, lines);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Marks this reservation permanent: {@code HELD -> COMMITTED}. Does not touch
     * {@code quantity_available} — the decrement already happened when the reservation was
     * created (database-design.md §3.4: "COMMIT rows record 0 ... the decrement already happened
     * at RESERVE time"). The caller ({@code CommitReservationUseCase}) is responsible for writing
     * the corresponding {@code stock_movements} rows after this call succeeds.
     *
     * @throws InvalidReservationStateException if this reservation is not currently {@code HELD}
     */
    public void commit() {
        requireHeld();
        this.status = ReservationStatus.COMMITTED;
    }

    /**
     * Releases this reservation's hold: {@code HELD -> RELEASED}. This method only flips the
     * status — restocking {@code quantity_available} and writing {@code stock_movements} rows is
     * the caller's ({@code ReleaseReservationUseCase}) responsibility, since those are
     * repository-level side effects the aggregate itself does not perform (mirrors
     * {@code StockItem}'s reserve/release path: the atomic quantity change lives outside
     * load-modify-save).
     *
     * @throws InvalidReservationStateException if this reservation is not currently {@code HELD}
     */
    public void release() {
        requireHeld();
        this.status = ReservationStatus.RELEASED;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public boolean isHeld() {
        return status == ReservationStatus.HELD;
    }

    /** @return {@code true} if this reservation is still {@code HELD} and its expiry has passed as of {@code now}. */
    public boolean isExpired(Instant now) {
        return isHeld() && expiresAt.isExpiredAsOf(now);
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public ReservationId getId() { return id; }

    public CheckoutSessionId getCheckoutSessionId() { return checkoutSessionId; }

    public ReservationStatus getStatus() { return status; }

    public Expiry getExpiresAt() { return expiresAt; }

    public Instant getCreatedAt() { return createdAt; }

    /** Unmodifiable, defensively-copied view of this reservation's lines. */
    public List<ReservedLine> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireHeld() {
        if (status != ReservationStatus.HELD) {
            throw new InvalidReservationStateException(
                    "Reservation " + id + " is not HELD (status=" + status + ")");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockReservation that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

package com.ecommerce.order.domain.model;

import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for the order bounded context (domain-model.md §1/§2 {@code Order}).
 *
 * <p>Owns an <strong>immutable purchase snapshot</strong> (rule 3): {@link #lines} and
 * {@link #totals} are frozen at {@link #place} time and never change afterward — no method on this
 * class mutates either. Owns the one-directional {@link OrderStatus} transition graph
 * {@code PLACED -> PAID -> CONFIRMED -> SHIPPED -> DELIVERED}, with branches
 * {@code PLACED -> FAILED} (payment window lapse, rule 8) and
 * {@code PLACED|PAID|CONFIRMED -> CANCELLED} (customer cancel until shipped, rule 7; admin
 * lifecycle management) — enforced exactly, no other edge, never backward. Every transition
 * appends an {@link OrderStatusTransition} to {@link #statusHistory} (rule 12: "every transition
 * records timestamp + actor").
 *
 * <p><strong>References out by typed id only</strong> (domain-model.md §2): {@link #customerId}
 * and {@link #reservationId} — never object references. {@link #reservationId} is always present
 * (never null) — {@code V006__create_order_schema.sql}'s {@code orders.reservation_id} is
 * {@code NOT NULL}: every v1 order-creation path ({@code PlaceOrderFromCheckoutUseCase}) always
 * supplies one, sourced from an event whose {@code reservationId} is itself non-null by
 * construction (ADR-0004's migration-ruling note, followed here at the Java type level too, in
 * preference to the ADR's own prose which called it nullable for a general OO modelling reason
 * that doesn't reflect any actual v1 code path).
 *
 * <p><strong>{@code OrderLine}</strong> (domain-model.md §2/§3) is folded into {@link LineSnapshot}
 * — see that record's javadoc.
 *
 * <p>Created via {@link #place}; reconstituted from persistence via {@link #reconstitute}. No
 * public setters — state changes happen through named domain methods only.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<LineSnapshot> lines;
    private final OrderTotals totals;
    private final String couponCode;
    private final ReservationId reservationId;
    private final List<OrderStatusTransition> statusHistory;
    private final long version;
    private final Instant placedAt;
    private final Instant updatedAt;

    private Order(OrderId id, CustomerId customerId, OrderStatus status, List<LineSnapshot> lines,
                  OrderTotals totals, String couponCode, ReservationId reservationId,
                  List<OrderStatusTransition> statusHistory, long version, Instant placedAt, Instant updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("OrderId must not be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("CustomerId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("OrderStatus must not be null");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Order must hold at least one line");
        }
        if (totals == null) {
            throw new IllegalArgumentException("OrderTotals must not be null");
        }
        if (reservationId == null) {
            throw new IllegalArgumentException("ReservationId must not be null — see class javadoc");
        }
        if (statusHistory == null || statusHistory.isEmpty()) {
            throw new IllegalArgumentException("Order must hold at least one status history entry");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        if (placedAt == null) {
            throw new IllegalArgumentException("placedAt must not be null");
        }
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.lines = new ArrayList<>(lines);
        this.totals = totals;
        this.couponCode = couponCode;
        this.reservationId = reservationId;
        this.statusHistory = new ArrayList<>(statusHistory);
        this.version = version;
        this.placedAt = placedAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Creates a brand-new order in {@link OrderStatus#PLACED}, from checkout's hand-off data
     * (ADR-0004). Records the initial {@code NULL -> PLACED} transition.
     */
    public static Order place(OrderId id, CustomerId customerId, List<LineSnapshot> lines, OrderTotals totals,
                              String couponCode, ReservationId reservationId, Actor actor, Instant now) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(now, "now must not be null");
        OrderStatusTransition initial = new OrderStatusTransition(null, OrderStatus.PLACED, actor, now);
        return new Order(id, customerId, OrderStatus.PLACED, lines, totals, couponCode, reservationId,
                List.of(initial), 0L, now, now);
    }

    /** Factory for reconstitution from persistence. Restores exact state without re-running creation rules. */
    public static Order reconstitute(OrderId id, CustomerId customerId, OrderStatus status, List<LineSnapshot> lines,
                                     OrderTotals totals, String couponCode, ReservationId reservationId,
                                     List<OrderStatusTransition> statusHistory, long version, Instant placedAt,
                                     Instant updatedAt) {
        return new Order(id, customerId, status, lines, totals, couponCode, reservationId, statusHistory,
                version, placedAt, updatedAt);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour — the legal transition graph, exactly, no other edge
    // -------------------------------------------------------------------------

    /** {@code PLACED -> PAID} — payment confirmed (admin action, ADR-0004 §Decision item 3). */
    public void markPaid(Actor actor, Instant now) {
        transition(OrderStatus.PLACED, OrderStatus.PAID, actor, now);
    }

    /** {@code PAID -> CONFIRMED} — admin confirms the order for fulfilment. */
    public void confirm(Actor actor, Instant now) {
        transition(OrderStatus.PAID, OrderStatus.CONFIRMED, actor, now);
    }

    /** {@code CONFIRMED -> SHIPPED} — admin marks the order shipped. */
    public void ship(Actor actor, Instant now) {
        transition(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, actor, now);
    }

    /** {@code SHIPPED -> DELIVERED} — admin marks the order delivered. */
    public void deliver(Actor actor, Instant now) {
        transition(OrderStatus.SHIPPED, OrderStatus.DELIVERED, actor, now);
    }

    /**
     * {@code PLACED|PAID|CONFIRMED -> CANCELLED} — customer cancel until shipped (rule 7) or admin
     * lifecycle management. Callers are responsible for releasing the reservation via
     * {@code order.domain.port.out.ReservationPort} in the same transaction (ADR-0004 item 3) —
     * this method only flips status and records history, mirroring
     * {@code StockReservation#release()}'s division of responsibility.
     *
     * @throws InvalidOrderTransitionException if the order is already {@code SHIPPED},
     *         {@code DELIVERED}, {@code FAILED}, or already {@code CANCELLED}
     */
    public void cancel(Actor actor, Instant now) {
        requireOneOf(OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.CONFIRMED);
        applyTransition(OrderStatus.CANCELLED, actor, now);
    }

    /**
     * {@code PLACED -> FAILED} — payment window lapsed (rule 8). Callers must NOT also release the
     * reservation here — checkout already released it synchronously before publishing
     * {@code CheckoutSessionExpiredEvent} (ADR-0004 sanity check); calling release again would hit
     * an already-released-state error.
     */
    public void fail(Actor actor, Instant now) {
        transition(OrderStatus.PLACED, OrderStatus.FAILED, actor, now);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Unmodifiable, defensively-copied view of this order's immutable line snapshots. */
    public List<LineSnapshot> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /** Unmodifiable, defensively-copied view of this order's full status history, oldest first. */
    public List<OrderStatusTransition> getStatusHistory() {
        return Collections.unmodifiableList(new ArrayList<>(statusHistory));
    }

    /** The single transition just appended by the most recent domain method call (or the initial PLACED row). */
    public OrderStatusTransition getLatestTransition() {
        return statusHistory.get(statusHistory.size() - 1);
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public OrderId getId() { return id; }

    public CustomerId getCustomerId() { return customerId; }

    public OrderStatus getStatus() { return status; }

    public OrderTotals getTotals() { return totals; }

    public String getCouponCode() { return couponCode; }

    public ReservationId getReservationId() { return reservationId; }

    public long getVersion() { return version; }

    public Instant getPlacedAt() { return placedAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void transition(OrderStatus requiredFrom, OrderStatus to, Actor actor, Instant now) {
        requireOneOf(requiredFrom);
        applyTransition(to, actor, now);
    }

    private void applyTransition(OrderStatus to, Actor actor, Instant now) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(now, "now must not be null");
        OrderStatus from = this.status;
        this.status = to;
        this.statusHistory.add(new OrderStatusTransition(from, to, actor, now));
    }

    private void requireOneOf(OrderStatus... legalSources) {
        for (OrderStatus legal : legalSources) {
            if (status == legal) {
                return;
            }
        }
        throw new InvalidOrderTransitionException(
                "Order " + id + " is not in a legal source status for this transition (status=" + status + ")");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

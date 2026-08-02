package com.ecommerce.order.domain;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;
import java.util.Optional;

/**
 * The single repository interface for the {@link Order} aggregate root (domain-model.md §2
 * ownership rule: "one repository per aggregate root, nothing else — no {@code OrderLineRepository}").
 * {@code order_items}/{@code order_status_history} are owned entities inside this aggregate —
 * persisting them is this interface's implementation's job, transparently, never a separate
 * repository.
 *
 * <p>Implemented by {@code order.infrastructure.persistence.JpaOrderRepository}.
 */
public interface OrderRepository {

    /**
     * Persists a brand-new order (header + line snapshots + the single initial PLACED history
     * row).
     *
     * @throws com.ecommerce.order.domain.exception.DuplicateOrderException if
     *         {@code uq_orders_reservation} is violated — a duplicate {@code AFTER_COMMIT} firing
     *         for the same reservation (ADR-0004 idempotency backstop)
     */
    Order create(Order order);

    /**
     * Persists a status transition already applied to {@code order} in memory: the header
     * (status/optimistic version check) plus exactly the one newest
     * {@code OrderStatusTransition} ({@link Order#getLatestTransition()}). Never touches line
     * snapshots or earlier history rows (both are write-once — {@code order_items}/
     * {@code order_status_history} grants are {@code SELECT, INSERT} only).
     *
     * @throws com.ecommerce.order.domain.exception.OrderConcurrentModificationException if the
     *         optimistic-lock version check fails (concurrent transition raced this one)
     */
    Order save(Order order);

    /** Admin lookups by bare id — no ownership scoping (security-architecture §3.4 admin row). */
    Optional<Order> findById(OrderId id);

    /**
     * Ownership-scoped read (security-architecture §3.2/§3.3): empty if the order does not exist
     * or belongs to a different customer — both cases return empty identically, so
     * {@code OrderNotFoundException} naturally covers both.
     */
    Optional<Order> findByIdAndCustomerId(OrderId id, CustomerId customerId);

    /**
     * Idempotency guard for {@code PlaceOrderFromCheckoutUseCase}'s {@code AFTER_COMMIT} listener
     * (ADR-0004) — at most one order per reservation ({@code uq_orders_reservation}). Also used by
     * {@code FailOrderFromCheckoutExpiryUseCase} to locate the order (if any) a
     * {@code CheckoutSessionExpiredEvent} corresponds to.
     */
    Optional<Order> findByReservationId(ReservationId reservationId);

    /**
     * Paginated order history for one customer, newest first (serves
     * {@code idx_orders_customer_placed}; api-guidelines §4). Returns a lightweight
     * {@link OrderSummary} projection, not the full aggregate — the JPA handoff note
     * (database-design.md §7): "Projections for list views ... never load entities for 5 columns".
     */
    PageResult<OrderSummary> findByCustomerId(CustomerId customerId, int page, int size);

    /**
     * Lightweight list-row projection matching {@code idx_orders_customer_placed}'s covering
     * {@code INCLUDE} columns (status, grand_total, currency) — an index-only scan serves this
     * without touching the heap.
     */
    record OrderSummary(OrderId id, String status, Money grandTotal, Instant placedAt) {}
}

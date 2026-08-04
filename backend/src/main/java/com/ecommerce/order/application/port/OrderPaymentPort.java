package com.ecommerce.order.application.port;

import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;

import java.util.Objects;

/**
 * Outbound-facing public façade order exposes to other bounded contexts that need to read a
 * customer's order for payment purposes and drive its {@code PLACED -> PAID} transition, without
 * depending on order's internal domain model or its concrete use cases (forbidden targets per
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}, ADR-0001).
 *
 * <p><strong>Order's first-ever producer-side façade</strong> (ADR-0005 §Decision item 1): every
 * prior context that needed to be called cross-context ({@code catalog}, {@code cart},
 * {@code pricing}, {@code inventory}) already exposes an {@code application.port} façade; order
 * had only ever been a <em>consumer</em> of others' façades until payment needed to drive its
 * {@code PLACED -> PAID} transition. Mirrors
 * {@code inventory.application.port.StockReservationPort}'s shape exactly: interface + nested
 * projection/exception classes in one file, implemented by a thin
 * {@code order.infrastructure.adapter.OrderPaymentAdapter} delegating to order's own use cases.
 *
 * <p><strong>Each call = that use case's own transaction</strong> — which, per ADR-0001's own
 * premise ("one deployment and one DB"), is the <em>same physical DB transaction</em> the calling
 * payment use case is already running, joined via default Spring {@code REQUIRED} propagation, not
 * a new one (ADR-0005 §Rationale item 1: the whole approved-charge sequence must be one atomic DB
 * transaction).
 */
public interface OrderPaymentPort {

    /**
     * Ownership-scoped read of an order for payment purposes.
     *
     * @throws OrderNotFoundException if the order does not exist or does not belong to
     *         {@code customerId} — translated from order's own
     *         {@code order.domain.exception.OrderNotFoundException}
     */
    OrderPaymentView getForPayment(OrderId orderId, CustomerId customerId);

    /**
     * Drives {@code Order PLACED -> PAID} (payment approved) — commits the reservation and
     * publishes {@code OrderPaidEvent} in the same transaction (ADR-0005 §Decision item 1,
     * mirroring {@code AdminMarkOrderPaidUseCase}'s body but with {@code Actor.system()}, no
     * {@code @PreAuthorize}, and no {@code AuditLogPort} write).
     *
     * <p><strong>The correctness guarantee, not the read-time check in {@link #getForPayment}</strong>
     * (ADR-0005 sanity check): reuses {@code Order.markPaid}'s own guard
     * ({@code InvalidOrderTransitionException} if not currently {@code PLACED}) rather than
     * duplicating a status check in the write path.
     *
     * @throws OrderNotFoundException if the order does not exist — translated from order's own
     *         {@code order.domain.exception.OrderNotFoundException}
     * @throws OrderNotPayableException if the order is not currently {@code PLACED} — translated
     *         from order's own {@code order.domain.exception.InvalidOrderTransitionException}
     */
    void markPaid(OrderId orderId);

    /** Read-only projection of what payment needs to know about an order (ADR-0005 §Decision item 1). */
    record OrderPaymentView(OrderId orderId, CustomerId customerId, OrderStatus status, Money grandTotal,
                             ReservationId reservationId) {

        public OrderPaymentView {
            Objects.requireNonNull(orderId, "OrderPaymentView orderId must not be null");
            Objects.requireNonNull(customerId, "OrderPaymentView customerId must not be null");
            Objects.requireNonNull(status, "OrderPaymentView status must not be null");
            Objects.requireNonNull(grandTotal, "OrderPaymentView grandTotal must not be null");
            Objects.requireNonNull(reservationId, "OrderPaymentView reservationId must not be null");
        }
    }

    /**
     * Unchecked, nested exception owned by this façade — never
     * {@code order.domain.exception.OrderNotFoundException}, which a cross-context caller must not
     * import (ADR-0005 §Decision item 1, mirroring ADR-0003 §Decision item 2's rule for
     * {@code StockReservationPort}).
     */
    class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Unchecked, nested exception owned by this façade — never
     * {@code order.domain.exception.InvalidOrderTransitionException}, which a cross-context caller
     * must not import. Order not currently {@code PLACED}.
     */
    class OrderNotPayableException extends RuntimeException {
        public OrderNotPayableException(String message) {
            super(message);
        }
    }
}

package com.ecommerce.payment.domain.port.out;

import com.ecommerce.payment.domain.exception.OrderNotFoundException;
import com.ecommerce.payment.domain.exception.OrderNotPayableException;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;

import java.util.Objects;

/**
 * Payment's own outbound port representing what it needs from the order context — deliberately
 * payment-shaped (ADR-0005 §Decision item 1), mirroring
 * {@code order.domain.port.out.ReservationPort}'s relationship to inventory's
 * {@code StockReservationPort}. Payment's own, independently-named mirror of
 * {@code order.application.port.OrderPaymentPort} — its own DTOs ({@link OrderView}), never order's
 * {@code OrderPaymentView} or {@code order.domain.model.OrderStatus} directly (ADR-0005: "no
 * pattern deviation from ADR-0003 item 2").
 *
 * <p>Implemented by {@code payment.infrastructure.adapter.OrderAdapter}, whose only dependency on
 * order is the order-owned {@code com.ecommerce.order.application.port.OrderPaymentPort} bean — the
 * sanctioned crossing point.
 */
public interface OrderPort {

    /**
     * Ownership-scoped read of an order for payment purposes.
     *
     * @throws OrderNotFoundException if the order does not exist or does not belong to
     *         {@code customerId} — translated from
     *         {@code OrderPaymentPort.OrderNotFoundException}
     */
    OrderView getForPayment(OrderId orderId, CustomerId customerId);

    /**
     * Drives {@code Order PLACED -> PAID} — must only ever be called after a gateway
     * {@code APPROVED} outcome (ADR-0005 items 2/3: never for {@code DECLINED}/{@code TIMEOUT}, and
     * never any method that transitions an order to {@code FAILED} or {@code CANCELLED}).
     *
     * @throws OrderNotFoundException if the order does not exist — translated from
     *         {@code OrderPaymentPort.OrderNotFoundException}
     * @throws OrderNotPayableException if the order is not currently payable — translated from
     *         {@code OrderPaymentPort.OrderNotPayableException} (the correctness guarantee backing
     *         {@code SubmitPaymentUseCase}'s own read-time fast-fail check)
     */
    void markPaid(OrderId orderId);

    /**
     * Read-only projection of what payment needs to know about an order — payment's own DTO,
     * deliberately independent of order's {@code OrderPaymentView} (ADR-0005 §Decision item 1).
     *
     * @param payable whether this order is currently payable (i.e. {@code PLACED}) — expressed in
     *                payment's own ubiquitous language rather than exposing order's
     *                {@code OrderStatus} enum across the context boundary; every other order status
     *                (PAID/CONFIRMED/SHIPPED/DELIVERED/FAILED/CANCELLED) is equally "not payable"
     *                from payment's perspective, so no finer distinction is carried
     */
    record OrderView(OrderId orderId, CustomerId customerId, boolean payable, Money grandTotal,
                      ReservationId reservationId) {

        public OrderView {
            Objects.requireNonNull(orderId, "OrderView orderId must not be null");
            Objects.requireNonNull(customerId, "OrderView customerId must not be null");
            Objects.requireNonNull(grandTotal, "OrderView grandTotal must not be null");
            Objects.requireNonNull(reservationId, "OrderView reservationId must not be null");
        }
    }
}

package com.ecommerce.payment.infrastructure.adapter;

import com.ecommerce.order.application.port.OrderPaymentPort;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.payment.domain.exception.OrderNotFoundException;
import com.ecommerce.payment.domain.exception.OrderNotPayableException;
import com.ecommerce.payment.domain.port.out.OrderPort;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;

import java.util.Objects;

/**
 * Implements payment's {@link OrderPort} by delegating to order's public
 * {@link OrderPaymentPort} — the only dependency this adapter (and, transitively, the payment
 * context) has on the order context (ADR-0005 §Decision item 1).
 *
 * <p>Translates order's façade-owned nested exceptions into payment's own domain exceptions —
 * payment's application/domain layers never see an order type.
 *
 * <p><strong>Interprets {@code OrderPaymentPort.OrderPaymentView.status()}
 * ({@code order.domain.model.OrderStatus}) to compute {@link OrderPort.OrderView#payable()}</strong>
 * — the one place in payment's codebase that references an order domain-model type, unavoidable
 * given {@code OrderPaymentPort}'s literal field type (ADR-0005 §Decision item 1's code block).
 * This is reached only via the sanctioned {@code application.port} DTO field, not via a direct
 * import of order's internal domain package for its own sake — payment's own {@link OrderPort}
 * mirror carries only the derived boolean onward, never the enum itself, so no other payment class
 * needs to know this type exists.
 */
public class OrderAdapter implements OrderPort {

    private final OrderPaymentPort orderPaymentPort;

    public OrderAdapter(OrderPaymentPort orderPaymentPort) {
        this.orderPaymentPort = Objects.requireNonNull(orderPaymentPort);
    }

    @Override
    public OrderView getForPayment(OrderId orderId, CustomerId customerId) {
        OrderPaymentPort.OrderPaymentView view;
        try {
            view = orderPaymentPort.getForPayment(orderId, customerId);
        } catch (OrderPaymentPort.OrderNotFoundException e) {
            throw new OrderNotFoundException(e.getMessage());
        }
        boolean payable = view.status() == OrderStatus.PLACED;
        return new OrderView(view.orderId(), view.customerId(), payable, view.grandTotal(), view.reservationId());
    }

    @Override
    public void markPaid(OrderId orderId) {
        try {
            orderPaymentPort.markPaid(orderId);
        } catch (OrderPaymentPort.OrderNotFoundException e) {
            throw new OrderNotFoundException(e.getMessage());
        } catch (OrderPaymentPort.OrderNotPayableException e) {
            throw new OrderNotPayableException(e.getMessage());
        }
    }
}

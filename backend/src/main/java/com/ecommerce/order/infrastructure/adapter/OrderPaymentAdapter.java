package com.ecommerce.order.infrastructure.adapter;

import com.ecommerce.order.application.port.OrderPaymentPort;
import com.ecommerce.order.application.usecase.GetOrderForPaymentUseCase;
import com.ecommerce.order.application.usecase.MarkOrderPaidFromPaymentUseCase;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;

import java.util.Objects;

/**
 * Implements order's {@link OrderPaymentPort} by delegating to
 * {@link GetOrderForPaymentUseCase}/{@link MarkOrderPaidFromPaymentUseCase} — the only dependency
 * this adapter has on order's application internals. Mirrors
 * {@code inventory.infrastructure.adapter.StockReservationAdapter}.
 *
 * <p>Translates order's own domain exceptions into this façade's nested exception types — a
 * cross-context caller (payment) must never catch or import an order {@code domain.exception} type
 * (ADR-0005 §Decision item 1, mirroring ADR-0003 §Decision item 2).
 *
 * <p><strong>Fully-qualifies the caught exceptions deliberately</strong>: {@link OrderPaymentPort}
 * declares nested types with the identical simple names ({@code OrderNotFoundException},
 * {@code OrderNotPayableException}) as order's own {@code domain.exception} classes. Because this
 * class {@code implements OrderPaymentPort}, those nested types are inherited members and would
 * shadow an unqualified import of the domain exception at unqualified-name resolution (JLS
 * member-type inheritance) — same hazard {@code StockReservationAdapter} documents and avoids by
 * fully qualifying the caught type. An unqualified catch here would silently bind to the nested
 * façade type instead of the domain exception the use cases actually throw, and the catch clause
 * would never fire.
 */
public class OrderPaymentAdapter implements OrderPaymentPort {

    private final GetOrderForPaymentUseCase getOrderForPaymentUseCase;
    private final MarkOrderPaidFromPaymentUseCase markOrderPaidFromPaymentUseCase;

    public OrderPaymentAdapter(GetOrderForPaymentUseCase getOrderForPaymentUseCase,
                               MarkOrderPaidFromPaymentUseCase markOrderPaidFromPaymentUseCase) {
        this.getOrderForPaymentUseCase = Objects.requireNonNull(getOrderForPaymentUseCase);
        this.markOrderPaidFromPaymentUseCase = Objects.requireNonNull(markOrderPaidFromPaymentUseCase);
    }

    @Override
    public OrderPaymentView getForPayment(OrderId orderId, CustomerId customerId) {
        Order order;
        try {
            order = getOrderForPaymentUseCase.execute(orderId, customerId);
        } catch (com.ecommerce.order.domain.exception.OrderNotFoundException e) {
            // Fully qualified deliberately — see class javadoc shadowing note.
            throw new OrderPaymentPort.OrderNotFoundException(e.getMessage());
        }
        return new OrderPaymentView(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotals().grandTotal(), order.getReservationId());
    }

    @Override
    public void markPaid(OrderId orderId) {
        try {
            markOrderPaidFromPaymentUseCase.execute(orderId);
        } catch (com.ecommerce.order.domain.exception.OrderNotFoundException e) {
            // Fully qualified deliberately — see class javadoc shadowing note.
            throw new OrderPaymentPort.OrderNotFoundException(e.getMessage());
        } catch (InvalidOrderTransitionException e) {
            throw new OrderPaymentPort.OrderNotPayableException(e.getMessage());
        }
    }
}

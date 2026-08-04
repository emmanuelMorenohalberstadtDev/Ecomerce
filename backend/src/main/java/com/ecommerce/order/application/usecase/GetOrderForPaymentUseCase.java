package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Ownership-scoped read of an order for payment purposes — backs
 * {@code order.application.port.OrderPaymentPort#getForPayment} (ADR-0005 §Decision item 1).
 * Reuses the existing {@link OrderRepository#findByIdAndCustomerId} — no new repository method.
 *
 * <p><strong>Not exposed over REST and not {@code @PreAuthorize}-gated</strong>: unlike
 * {@link GetOrderUseCase} (the customer-facing twin this class otherwise mirrors), this use case
 * has no direct HTTP entry point — its only caller is
 * {@code order.infrastructure.adapter.OrderPaymentAdapter}, reached exclusively through payment's
 * already-{@code @PreAuthorize("hasRole('CUSTOMER')")}-gated {@code SubmitPaymentUseCase}. Mirrors
 * {@code inventory.application.usecase.ReserveStockUseCase}'s equivalent choice (an internal,
 * port-only use case carries no auth annotation of its own — the REST-facing entry point at the
 * top of the call chain is the single place that gate belongs).
 *
 * <p>Read-only transaction boundary — joins the caller's (payment's) transaction via default
 * Spring {@code REQUIRED} propagation when invoked mid-flow, or runs standalone when called first.
 */
@Service
@Transactional(readOnly = true)
public class GetOrderForPaymentUseCase {

    private final OrderRepository orderRepository;

    public GetOrderForPaymentUseCase(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    public Order execute(OrderId orderId, CustomerId customerId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "No order " + orderId + " for the current customer"));
    }
}

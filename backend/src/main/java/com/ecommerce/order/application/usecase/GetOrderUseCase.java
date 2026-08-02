package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Returns a single order, ownership-scoped to the caller (security-architecture §3.2/§3.3: "the
 * repository query scopes by owner", "ownership failures are 404, never 403"). Mirrors
 * {@code checkout.application.usecase.GetCheckoutSessionUseCase} exactly.
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public Order execute(OrderId orderId, CustomerId customerId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "No order " + orderId + " for the current customer"));
    }
}

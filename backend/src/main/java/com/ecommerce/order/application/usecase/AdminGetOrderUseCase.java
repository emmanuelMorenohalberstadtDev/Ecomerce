package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.shared.id.OrderId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Returns a single order by bare id — no ownership scoping (security-architecture §3.4 "Admin
 * ops" row: role-gated only, no ownership dimension). Backs
 * {@code GET /api/v1/admin/orders/{id}}.
 *
 * <p>Kept as its own use case (rather than {@code AdminOrderController} calling
 * {@link OrderRepository} directly) so the controller never depends on a {@code domain} repository
 * type — presentation depends on application only (backend-architecture §1 dependency rule).
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class AdminGetOrderUseCase {

    private final OrderRepository orderRepository;

    public AdminGetOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Order execute(OrderId orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));
    }
}

package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.OrderRepository.OrderSummary;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.shared.id.CustomerId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Paginated order history for the current customer, newest first — serves
 * {@code idx_orders_customer_placed} (database-design.md §5) via
 * {@link OrderRepository#findByCustomerId}. Sort is fixed to {@code placedAt DESC}; no
 * client-supplied sort field in v1 (api-guidelines §4.4 requires an allowlist per endpoint — the
 * allowlist here is exactly one entry, so no sort parameter is exposed at all).
 *
 * <p>Page size clamping (api-guidelines §4.2: hard cap 100) is enforced at the presentation layer
 * ({@code OrderController}), not here — this use case trusts its caller's {@code page}/{@code size}
 * inputs, mirroring every other paginated use case in this codebase.
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class ListOrdersUseCase {

    private final OrderRepository orderRepository;

    public ListOrdersUseCase(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResult<OrderSummary> execute(CustomerId customerId, int page, int size) {
        return orderRepository.findByCustomerId(customerId, page, size);
    }
}

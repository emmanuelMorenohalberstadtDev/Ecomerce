package com.ecommerce.order.presentation;

import com.ecommerce.order.application.usecase.CancelOrderUseCase;
import com.ecommerce.order.application.usecase.GetOrderUseCase;
import com.ecommerce.order.application.usecase.ListOrdersUseCase;
import com.ecommerce.order.domain.OrderRepository.OrderSummary;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.order.presentation.dto.OrderResponse;
import com.ecommerce.order.presentation.dto.OrderSummaryResponse;
import com.ecommerce.order.presentation.dto.PageResponse;
import com.ecommerce.order.presentation.mapper.OrderMapper;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.UserId;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for a customer's own orders: {@code /api/v1/orders}.
 *
 * <p>Route set:
 * <ul>
 *   <li>{@code GET  /api/v1/orders}                    — paginated order history, own only</li>
 *   <li>{@code GET  /api/v1/orders/{id}}                — order detail, own only</li>
 *   <li>{@code POST /api/v1/orders/{id}/cancellation}   — customer cancel (rule 7: until shipped)</li>
 * </ul>
 *
 * <p><strong>Resource naming (api-guidelines §1.2/§1.3):</strong> cancellation is a sub-resource,
 * {@code POST .../{id}/cancellation} — "the transition is the thing being created" (§1.3), not a
 * {@code PATCH status=X} tunnel, mirroring {@code CheckoutController}'s {@code .../confirmation}.
 *
 * <p><strong>Auth (security-architecture §3.4 "Customer-scoped reads/ops" row):</strong> GUEST —
 * 401 (no {@code permitAll()} entry for {@code /api/v1/orders/**}; falls through to
 * {@code .anyRequest().authenticated()}); CUSTOMER — own only, else 404; ADMIN — 403
 * ({@code @PreAuthorize("hasRole('CUSTOMER')")} on every use case, security §3.2 fine layer — admin
 * order access is a separate surface, {@code AdminOrderController}). Because there is no guest
 * path, {@link #resolveCustomerId} never returns {@code null}, mirroring
 * {@code CheckoutController}'s identical reasoning.
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>GET /orders</td><td>401</td><td>200</td><td>403</td><td>own orders only</td></tr>
 *   <tr><td>GET /orders/{id}</td><td>401</td><td>200 or 404</td><td>403</td><td>404 if not the caller's order</td></tr>
 *   <tr><td>POST /orders/{id}/cancellation</td><td>401</td><td>200, 404, or 422</td><td>403</td>
 *       <td>404 if not the caller's order; 422 if not PLACED|PAID|CONFIRMED</td></tr>
 * </table>
 *
 * <p>No try/catch — error translation is handled entirely by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final ListOrdersUseCase listOrdersUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderController(ListOrdersUseCase listOrdersUseCase,
                           GetOrderUseCase getOrderUseCase,
                           CancelOrderUseCase cancelOrderUseCase) {
        this.listOrdersUseCase = listOrdersUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        CustomerId customerId = resolveCustomerId(authentication);
        int clampedSize = Math.min(size, 100); // api-guidelines §4.2: hard cap 100, clamped not rejected

        PageResult<OrderSummary> result = listOrdersUseCase.execute(customerId, page, clampedSize);

        return new PageResponse<>(
                result.content().stream().map(OrderMapper::toSummaryResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    public OrderResponse getById(Authentication authentication, @PathVariable UUID id) {
        CustomerId customerId = resolveCustomerId(authentication);
        Order order = getOrderUseCase.execute(OrderId.of(id), customerId);
        return OrderMapper.toResponse(order);
    }

    @PostMapping("/{id}/cancellation")
    public OrderResponse cancel(Authentication authentication, @PathVariable UUID id) {
        CustomerId customerId = resolveCustomerId(authentication);
        Order order = cancelOrderUseCase.execute(OrderId.of(id), customerId);
        return OrderMapper.toResponse(order);
    }

    /**
     * Extracts the caller's {@link CustomerId} from the JWT-derived principal. Orders have no
     * guest path (security-architecture §3.4) — unlike {@code CartController.resolveCustomerId},
     * this never returns {@code null}: the coarse route rule already rejects an unauthenticated
     * caller with 401 before this method ever runs, so {@code authentication} is always a genuine,
     * non-anonymous principal here. The check below is defensive only.
     */
    private static CustomerId resolveCustomerId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserId userId)) {
            throw new IllegalStateException(
                    "Unauthenticated request reached OrderController — the security filter chain "
                            + "should have rejected this with 401 before the controller ran");
        }
        return CustomerId.of(userId.value());
    }
}

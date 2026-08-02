package com.ecommerce.order.presentation;

import com.ecommerce.order.application.usecase.AdminCancelOrderUseCase;
import com.ecommerce.order.application.usecase.AdminConfirmOrderUseCase;
import com.ecommerce.order.application.usecase.AdminDeliverOrderUseCase;
import com.ecommerce.order.application.usecase.AdminGetOrderUseCase;
import com.ecommerce.order.application.usecase.AdminMarkOrderPaidUseCase;
import com.ecommerce.order.application.usecase.AdminShipOrderUseCase;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.presentation.dto.OrderResponse;
import com.ecommerce.order.presentation.mapper.OrderMapper;
import com.ecommerce.shared.id.OrderId;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin order-lifecycle management: {@code /api/v1/admin/orders}. The route matcher in
 * {@code AuthSecurityConfiguration} already requires {@code ADMIN} at the coarse layer for
 * everything under {@code /api/v1/admin/**}; each use case additionally carries
 * {@code @PreAuthorize("hasRole('ADMIN')")} as the fine-grained second layer
 * (security-architecture §3.2). Every mutation writes one row to the admin audit log
 * (security §6c) via order's own {@code AuditLogPort}, in the same transaction as the mutation.
 *
 * <p>Route set — state transitions as sub-resource POSTs (api-guidelines §1.3), named per the
 * lifecycle event they represent, mirroring {@code CheckoutController}'s {@code .../confirmation}
 * pattern rather than a {@code PATCH status=X} tunnel:
 * <ul>
 *   <li>{@code GET  /api/v1/admin/orders/{id}}                     — order detail, any customer's</li>
 *   <li>{@code POST /api/v1/admin/orders/{id}/payment-confirmation} — PLACED -> PAID</li>
 *   <li>{@code POST /api/v1/admin/orders/{id}/confirmation}         — PAID -> CONFIRMED</li>
 *   <li>{@code POST /api/v1/admin/orders/{id}/shipment}             — CONFIRMED -> SHIPPED</li>
 *   <li>{@code POST /api/v1/admin/orders/{id}/delivery}             — SHIPPED -> DELIVERED</li>
 *   <li>{@code POST /api/v1/admin/orders/{id}/cancellation}         — PLACED|PAID|CONFIRMED -> CANCELLED</li>
 * </ul>
 *
 * <p><strong>No admin order-list endpoint</strong> and <strong>no admin manual-fail endpoint</strong>
 * in this change — security-architecture §3.4's admin row scopes this surface to "order lifecycle
 * mgmt only, audited"; domain-model.md/ADR-0004 give no reason for an admin-manual-fail transition
 * (the only {@code PLACED -> FAILED} path in v1 is the checkout payment-window-expiry listener) or
 * for a cross-customer order-browsing screen, so neither is built here (avoids inventing scope, per
 * the task brief). See the handoff report for this as a documented, deliberate gap, not an
 * oversight.
 *
 * <p>Auth decision table — every endpoint below (security-architecture §3.4 "Admin ops" row: no
 * ownership dimension, role-gated only):
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>GET /admin/orders/{id}</td><td>401</td><td>403</td><td>200 or 404</td><td>404 if id doesn't exist</td></tr>
 *   <tr><td>POST .../payment-confirmation</td><td>401</td><td>403</td><td>200 or 422</td><td>422 if not PLACED</td></tr>
 *   <tr><td>POST .../confirmation</td><td>401</td><td>403</td><td>200 or 422</td><td>422 if not PAID</td></tr>
 *   <tr><td>POST .../shipment</td><td>401</td><td>403</td><td>200 or 422</td><td>422 if not CONFIRMED</td></tr>
 *   <tr><td>POST .../delivery</td><td>401</td><td>403</td><td>200 or 422</td><td>422 if not SHIPPED</td></tr>
 *   <tr><td>POST .../cancellation</td><td>401</td><td>403</td><td>200 or 422</td><td>422 if not PLACED|PAID|CONFIRMED</td></tr>
 * </table>
 *
 * <p>No try/catch — error translation is handled entirely by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Validated
public class AdminOrderController {

    private final AdminGetOrderUseCase adminGetOrderUseCase;
    private final AdminMarkOrderPaidUseCase adminMarkOrderPaidUseCase;
    private final AdminConfirmOrderUseCase adminConfirmOrderUseCase;
    private final AdminShipOrderUseCase adminShipOrderUseCase;
    private final AdminDeliverOrderUseCase adminDeliverOrderUseCase;
    private final AdminCancelOrderUseCase adminCancelOrderUseCase;

    public AdminOrderController(AdminGetOrderUseCase adminGetOrderUseCase,
                                AdminMarkOrderPaidUseCase adminMarkOrderPaidUseCase,
                                AdminConfirmOrderUseCase adminConfirmOrderUseCase,
                                AdminShipOrderUseCase adminShipOrderUseCase,
                                AdminDeliverOrderUseCase adminDeliverOrderUseCase,
                                AdminCancelOrderUseCase adminCancelOrderUseCase) {
        this.adminGetOrderUseCase = adminGetOrderUseCase;
        this.adminMarkOrderPaidUseCase = adminMarkOrderPaidUseCase;
        this.adminConfirmOrderUseCase = adminConfirmOrderUseCase;
        this.adminShipOrderUseCase = adminShipOrderUseCase;
        this.adminDeliverOrderUseCase = adminDeliverOrderUseCase;
        this.adminCancelOrderUseCase = adminCancelOrderUseCase;
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        Order order = adminGetOrderUseCase.execute(OrderId.of(id));
        return OrderMapper.toResponse(order);
    }

    @PostMapping("/{id}/payment-confirmation")
    public OrderResponse markPaid(@PathVariable UUID id) {
        Order order = adminMarkOrderPaidUseCase.execute(OrderId.of(id));
        return OrderMapper.toResponse(order);
    }

    @PostMapping("/{id}/confirmation")
    public OrderResponse confirm(@PathVariable UUID id) {
        Order order = adminConfirmOrderUseCase.execute(OrderId.of(id));
        return OrderMapper.toResponse(order);
    }

    @PostMapping("/{id}/shipment")
    public OrderResponse ship(@PathVariable UUID id) {
        Order order = adminShipOrderUseCase.execute(OrderId.of(id));
        return OrderMapper.toResponse(order);
    }

    @PostMapping("/{id}/delivery")
    public OrderResponse deliver(@PathVariable UUID id) {
        Order order = adminDeliverOrderUseCase.execute(OrderId.of(id));
        return OrderMapper.toResponse(order);
    }

    @PostMapping("/{id}/cancellation")
    public OrderResponse cancel(@PathVariable UUID id) {
        Order order = adminCancelOrderUseCase.execute(OrderId.of(id));
        return OrderMapper.toResponse(order);
    }
}

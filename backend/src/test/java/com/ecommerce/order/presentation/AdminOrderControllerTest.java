package com.ecommerce.order.presentation;

import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.order.application.usecase.AdminCancelOrderUseCase;
import com.ecommerce.order.application.usecase.AdminConfirmOrderUseCase;
import com.ecommerce.order.application.usecase.AdminDeliverOrderUseCase;
import com.ecommerce.order.application.usecase.AdminGetOrderUseCase;
import com.ecommerce.order.application.usecase.AdminMarkOrderPaidUseCase;
import com.ecommerce.order.application.usecase.AdminShipOrderUseCase;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link AdminOrderController} using {@code @WebMvcTest}, mirroring
 * {@code catalog.presentation.AdminProductControllerTest}'s setup for the admin-only surface.
 *
 * <p>Every endpoint sits under {@code /api/v1/admin/**}, which {@link AuthSecurityConfiguration}
 * gates at the coarse route layer ({@code hasRole("ADMIN")}) — unlike {@code OrderController},
 * that means CUSTOMER -> 403 is provable directly in this slice (the route rule rejects before the
 * controller/mocked-use-case is ever reached), not only via {@code @PreAuthorize} on a
 * (here, mocked) use case bean.
 */
@Tag("unit")
@WebMvcTest(AdminOrderController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminGetOrderUseCase adminGetOrderUseCase;

    @MockBean
    private AdminMarkOrderPaidUseCase adminMarkOrderPaidUseCase;

    @MockBean
    private AdminConfirmOrderUseCase adminConfirmOrderUseCase;

    @MockBean
    private AdminShipOrderUseCase adminShipOrderUseCase;

    @MockBean
    private AdminDeliverOrderUseCase adminDeliverOrderUseCase;

    @MockBean
    private AdminCancelOrderUseCase adminCancelOrderUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    private Order placedOrder(OrderId orderId) {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        return Order.place(orderId, CustomerId.generate(), List.of(line), totals, null, ReservationId.generate(),
                Actor.system(), NOW);
    }

    // ── GET /api/v1/admin/orders/{id} ─────────────────────────────────────

    @Test
    void getById_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getById_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_returns200_whenAdminRole() throws Exception {
        Order order = placedOrder(OrderId.generate());
        when(adminGetOrderUseCase.execute(order.getId())).thenReturn(order);

        mockMvc.perform(get("/api/v1/admin/orders/{id}", order.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_returns404_whenOrderDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminGetOrderUseCase.execute(OrderId.of(id))).thenThrow(new OrderNotFoundException("No order " + id));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"));
    }

    // ── POST /api/v1/admin/orders/{id}/payment-confirmation ──────────────

    @Test
    void markPaid_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/payment-confirmation", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void markPaid_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/payment-confirmation", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void markPaid_returns200_whenAdminRole() throws Exception {
        Order order = placedOrder(OrderId.generate());
        order.markPaid(Actor.admin(UUID.randomUUID()), NOW);
        when(adminMarkOrderPaidUseCase.execute(order.getId())).thenReturn(order);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/payment-confirmation", order.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void markPaid_returns422_whenOrderNotPlaced() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminMarkOrderPaidUseCase.execute(OrderId.of(id)))
                .thenThrow(new InvalidOrderTransitionException("not in a legal source status"));

        mockMvc.perform(post("/api/v1/admin/orders/{id}/payment-confirmation", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/business-rule-violation"));
    }

    // ── POST /api/v1/admin/orders/{id}/confirmation ───────────────────────

    @Test
    void confirm_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/confirmation", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void confirm_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/confirmation", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirm_returns200_whenAdminRole() throws Exception {
        Order order = placedOrder(OrderId.generate());
        order.markPaid(Actor.admin(UUID.randomUUID()), NOW);
        order.confirm(Actor.admin(UUID.randomUUID()), NOW);
        when(adminConfirmOrderUseCase.execute(order.getId())).thenReturn(order);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/confirmation", order.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // ── POST /api/v1/admin/orders/{id}/shipment ───────────────────────────

    @Test
    void ship_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/shipment", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void ship_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/shipment", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ship_returns200_whenAdminRole() throws Exception {
        Order order = placedOrder(OrderId.generate());
        order.markPaid(Actor.admin(UUID.randomUUID()), NOW);
        order.confirm(Actor.admin(UUID.randomUUID()), NOW);
        order.ship(Actor.admin(UUID.randomUUID()), NOW);
        when(adminShipOrderUseCase.execute(order.getId())).thenReturn(order);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/shipment", order.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    // ── POST /api/v1/admin/orders/{id}/delivery ───────────────────────────

    @Test
    void deliver_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/delivery", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deliver_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/delivery", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deliver_returns200_whenAdminRole() throws Exception {
        Order order = placedOrder(OrderId.generate());
        order.markPaid(Actor.admin(UUID.randomUUID()), NOW);
        order.confirm(Actor.admin(UUID.randomUUID()), NOW);
        order.ship(Actor.admin(UUID.randomUUID()), NOW);
        order.deliver(Actor.admin(UUID.randomUUID()), NOW);
        when(adminDeliverOrderUseCase.execute(order.getId())).thenReturn(order);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/delivery", order.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    // ── POST /api/v1/admin/orders/{id}/cancellation ───────────────────────

    @Test
    void cancel_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/cancellation", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void cancel_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{id}/cancellation", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancel_returns200_whenAdminRole() throws Exception {
        Order order = placedOrder(OrderId.generate());
        order.cancel(Actor.admin(UUID.randomUUID()), NOW);
        when(adminCancelOrderUseCase.execute(order.getId())).thenReturn(order);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/cancellation", order.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancel_returns422_whenOrderNoLongerCancellable() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminCancelOrderUseCase.execute(OrderId.of(id)))
                .thenThrow(new InvalidOrderTransitionException("not in a legal source status"));

        mockMvc.perform(post("/api/v1/admin/orders/{id}/cancellation", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/business-rule-violation"));
    }
}

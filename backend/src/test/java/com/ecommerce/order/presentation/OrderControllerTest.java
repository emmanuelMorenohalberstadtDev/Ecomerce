package com.ecommerce.order.presentation;

import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.auth.infrastructure.security.JwtTokenService.JwtClaims;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.order.application.usecase.CancelOrderUseCase;
import com.ecommerce.order.application.usecase.GetOrderUseCase;
import com.ecommerce.order.application.usecase.ListOrdersUseCase;
import com.ecommerce.order.domain.OrderRepository.OrderSummary;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.id.UserId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link OrderController} using {@code @WebMvcTest}, mirroring
 * {@code checkout.presentation.CheckoutControllerTest}'s setup — the same real-JWT-principal
 * authentication style ({@code OrderController.resolveCustomerId} requires an actual
 * {@link UserId} principal, so a bare {@code @WithMockUser} is not enough).
 *
 * <p><strong>ADMIN -> 403 is intentionally not tested here</strong> — same documented limitation as
 * {@code CheckoutControllerTest}: {@code /api/v1/orders/**} has no {@code hasRole(...)} route rule
 * (falls through to the coarse {@code .anyRequest().authenticated()} rule), so an
 * ADMIN-authenticated request passes the route check and reaches the controller in this slice. The
 * actual 403 is enforced by {@code @PreAuthorize("hasRole('CUSTOMER')")} on each use case, not
 * reachable here because the use case bean is a Mockito mock.
 */
@Tag("unit")
@WebMvcTest(OrderController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListOrdersUseCase listOrdersUseCase;

    @MockBean
    private GetOrderUseCase getOrderUseCase;

    @MockBean
    private CancelOrderUseCase cancelOrderUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static final String TOKEN = "a-valid-looking-bearer-token";
    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    private final CustomerId customerId = CustomerId.generate();

    private void authenticateAsCustomer() {
        when(jwtTokenService.validateAndExtract(TOKEN)).thenReturn(new JwtClaims(UserId.of(customerId.value()), Role.CUSTOMER));
    }

    private Order placedOrder(OrderId orderId) {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        return Order.place(orderId, customerId, List.of(line), totals, null, ReservationId.generate(),
                Actor.system(), NOW);
    }

    // ── GET /api/v1/orders — auth decision table + pagination ────────────────

    @Test
    void list_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_returns200_withPaginationEnvelope_whenAuthenticated() throws Exception {
        authenticateAsCustomer();
        OrderSummary summary = new OrderSummary(OrderId.generate(), "PLACED",
                new Money(new BigDecimal("20.00"), "USD"), NOW);
        when(listOrdersUseCase.execute(customerId, 0, 20))
                .thenReturn(new PageResult<>(List.of(summary), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(summary.id().toString()))
                .andExpect(jsonPath("$.content[0].status").value("PLACED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_clampsPageSize_atHardCapOf100() throws Exception {
        authenticateAsCustomer();
        when(listOrdersUseCase.execute(eq(customerId), eq(0), eq(100)))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("size", "500"))
                .andExpect(status().isOk());

        verify(listOrdersUseCase).execute(customerId, 0, 100);
    }

    // ── GET /api/v1/orders/{id} — auth decision table ─────────────────────

    @Test
    void getById_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returns200_whenOwnedByCaller() throws Exception {
        authenticateAsCustomer();
        Order order = placedOrder(OrderId.generate());
        when(getOrderUseCase.execute(order.getId(), customerId)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/{id}", order.getId().value())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void getById_returns404_whenNotOwnedByCallerOrMissing() throws Exception {
        authenticateAsCustomer();
        UUID id = UUID.randomUUID();
        when(getOrderUseCase.execute(OrderId.of(id), customerId))
                .thenThrow(new OrderNotFoundException("No order for the current customer"));

        mockMvc.perform(get("/api/v1/orders/{id}", id)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getById_returns400_whenIdIsNotAValidUuid() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(get("/api/v1/orders/{id}", "not-a-uuid")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    // ── POST /api/v1/orders/{id}/cancellation — auth decision table ──────────

    @Test
    void cancel_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancellation", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_returns200_whenSuccessful() throws Exception {
        authenticateAsCustomer();
        Order order = placedOrder(OrderId.generate());
        order.cancel(Actor.customer(customerId.value()), NOW);
        when(cancelOrderUseCase.execute(order.getId(), customerId)).thenReturn(order);

        mockMvc.perform(post("/api/v1/orders/{id}/cancellation", order.getId().value())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_returns404_whenNotOwnedByCaller() throws Exception {
        authenticateAsCustomer();
        UUID id = UUID.randomUUID();
        when(cancelOrderUseCase.execute(OrderId.of(id), customerId))
                .thenThrow(new OrderNotFoundException("No order for the current customer"));

        mockMvc.perform(post("/api/v1/orders/{id}/cancellation", id)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"));
    }

    @Test
    void cancel_returns422_whenOrderNoLongerCancellable() throws Exception {
        authenticateAsCustomer();
        UUID id = UUID.randomUUID();
        when(cancelOrderUseCase.execute(OrderId.of(id), customerId))
                .thenThrow(new InvalidOrderTransitionException("Order is not in a legal source status"));

        mockMvc.perform(post("/api/v1/orders/{id}/cancellation", id)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/business-rule-violation"));
    }
}

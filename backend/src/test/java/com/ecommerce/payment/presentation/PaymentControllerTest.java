package com.ecommerce.payment.presentation;

import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.auth.infrastructure.security.JwtTokenService.JwtClaims;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase;
import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase.SubmitPaymentResult;
import com.ecommerce.payment.domain.exception.DuplicatePaymentException;
import com.ecommerce.payment.domain.exception.InvalidPaymentStateException;
import com.ecommerce.payment.domain.exception.OrderNotFoundException;
import com.ecommerce.payment.domain.exception.OrderNotPayableException;
import com.ecommerce.payment.domain.exception.PaymentConcurrentModificationException;
import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.id.UserId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link PaymentController} using {@code @WebMvcTest}, mirroring
 * {@code order.presentation.OrderControllerTest}'s setup — the same real-JWT-principal
 * authentication style ({@code PaymentController.resolveCustomerId} requires an actual
 * {@link UserId} principal).
 *
 * <p><strong>ADMIN -> 403 is intentionally not tested here</strong> — same documented limitation as
 * {@code OrderControllerTest}/{@code CheckoutControllerTest}: {@code /api/v1/orders/**} has no
 * {@code hasRole(...)} route rule (falls through to the coarse {@code .anyRequest().authenticated()}
 * rule), so an ADMIN-authenticated request passes the route check and reaches the controller in
 * this slice — the actual 403 is enforced by {@code @PreAuthorize("hasRole('CUSTOMER')")} on
 * {@link SubmitPaymentUseCase}, not reachable here because the use case bean is a Mockito mock.
 */
@Tag("unit")
@WebMvcTest(PaymentController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubmitPaymentUseCase submitPaymentUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static final String TOKEN = "a-valid-looking-bearer-token";
    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    private final UUID customerId = UUID.randomUUID();

    private void authenticateAsCustomer() {
        when(jwtTokenService.validateAndExtract(TOKEN)).thenReturn(new JwtClaims(UserId.of(customerId), Role.CUSTOMER));
    }

    private SubmitPaymentResult approvedResult(UUID orderId) {
        return new SubmitPaymentResult(PaymentId.generate(), PaymentStatus.CAPTURED, PaymentOutcome.APPROVED,
                null, "SIM-ref-1", new Money(new BigDecimal("25.00"), "USD"), NOW);
    }

    private SubmitPaymentResult declinedResult() {
        return new SubmitPaymentResult(PaymentId.generate(), PaymentStatus.PENDING, PaymentOutcome.DECLINED,
                DeclineReason.CARD_DECLINED, "SIM-ref-2", new Money(new BigDecimal("25.00"), "USD"), NOW);
    }

    // ── POST /api/v1/orders/{orderId}/payments — auth decision table ────────

    @Test
    void submitPayment_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitPayment_returns200_withCapturedStatus_whenGatewayApproves() throws Exception {
        authenticateAsCustomer();
        UUID orderId = UUID.randomUUID();
        when(submitPaymentUseCase.execute(any())).thenReturn(approvedResult(orderId));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.declineReason").doesNotExist())
                .andExpect(jsonPath("$.amount.amount").value("25.00"))
                .andExpect(jsonPath("$.amount.currency").value("USD"));
    }

    @Test
    void submitPayment_returns200_withPendingStatus_whenGatewayDeclines() throws Exception {
        authenticateAsCustomer();
        UUID orderId = UUID.randomUUID();
        when(submitPaymentUseCase.execute(any())).thenReturn(declinedResult());

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.outcome").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("CARD_DECLINED"));
    }

    @Test
    void submitPayment_returns400_whenIdempotencyKeyHeaderMissing() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    @Test
    void submitPayment_returns400_whenIdempotencyKeyHeaderIsBlank() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    @Test
    void submitPayment_returns400_whenIdempotencyKeyHeaderExceedsMaxLength() throws Exception {
        authenticateAsCustomer();
        String tooLong = "k".repeat(129);

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    @Test
    void submitPayment_returns404_whenOrderNotFound() throws Exception {
        authenticateAsCustomer();
        when(submitPaymentUseCase.execute(any()))
                .thenThrow(new OrderNotFoundException("No order for the current customer"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"));
    }

    @Test
    void submitPayment_returns422_whenOrderNotPayable() throws Exception {
        authenticateAsCustomer();
        when(submitPaymentUseCase.execute(any()))
                .thenThrow(new OrderNotPayableException("Order is not currently payable"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/business-rule-violation"));
    }

    @Test
    void submitPayment_returns422_whenPaymentInInvalidState() throws Exception {
        authenticateAsCustomer();
        when(submitPaymentUseCase.execute(any()))
                .thenThrow(new InvalidPaymentStateException("Payment is not PENDING"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/business-rule-violation"));
    }

    @Test
    void submitPayment_returns409_whenDuplicatePayment() throws Exception {
        authenticateAsCustomer();
        when(submitPaymentUseCase.execute(any()))
                .thenThrow(new DuplicatePaymentException("A payment already exists for this order"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/conflict"));
    }

    @Test
    void submitPayment_returns409_whenConcurrentModification() throws Exception {
        authenticateAsCustomer();
        when(submitPaymentUseCase.execute(any()))
                .thenThrow(new PaymentConcurrentModificationException("modified concurrently", null));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/conflict"));
    }

    @Test
    void submitPayment_returns400_whenOrderIdIsNotAValidUuid() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", "not-a-uuid")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }
}

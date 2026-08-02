package com.ecommerce.checkout.presentation;

import com.ecommerce.auth.domain.model.Role;
import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.auth.infrastructure.security.JwtTokenService.JwtClaims;
import com.ecommerce.checkout.application.usecase.CheckoutOutcome;
import com.ecommerce.checkout.application.usecase.ConfirmRecalculatedTotalUseCase;
import com.ecommerce.checkout.application.usecase.GetCheckoutSessionUseCase;
import com.ecommerce.checkout.application.usecase.StartCheckoutUseCase;
import com.ecommerce.checkout.domain.exception.CheckoutSessionNotFoundException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.id.UserId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link CheckoutController} using {@code @WebMvcTest}, mirroring
 * {@code cart.presentation.CartControllerTest}'s setup.
 *
 * <p><strong>Authenticating as a real customer principal:</strong> unlike cart (a
 * {@code permitAll()} route where a bare {@code @WithMockUser} is enough to exercise the
 * authenticated branch), {@link CheckoutController#resolveCustomerId} requires the
 * {@link org.springframework.security.core.Authentication#getPrincipal()} to be an actual
 * {@link UserId} instance — {@code @WithMockUser}'s default {@code UserDetails} principal would
 * hit that method's defensive {@code IllegalStateException} branch. Tests therefore authenticate
 * the same way production does: a {@code Authorization: Bearer <token>} header, with the
 * {@link JwtTokenService} bean mocked to resolve that opaque token to a given {@link UserId}/
 * {@link Role} — exactly what {@code JwtAuthenticationFilter} does with a real token.
 *
 * <p><strong>ADMIN → 403 is intentionally not tested here</strong> — same documented limitation as
 * {@code catalog.presentation.AdminProductControllerTest}: {@code checkout-sessions} has no
 * {@code hasRole(...)} route rule (it falls through to the coarse {@code .anyRequest().authenticated()}
 * rule, per {@link AuthSecurityConfiguration}), so an ADMIN-authenticated request passes the route
 * check and reaches the controller in a slice test. The actual 403 for ADMIN is enforced by
 * {@code @PreAuthorize("hasRole('CUSTOMER')")} on each use case, which is not reachable here because
 * the use case bean is a Mockito mock (no method-security proxy wraps a mock) — asserting 403 in
 * this tier would therefore assert something this slice cannot actually prove.
 */
@Tag("unit")
@WebMvcTest(CheckoutController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StartCheckoutUseCase startCheckoutUseCase;

    @MockBean
    private ConfirmRecalculatedTotalUseCase confirmRecalculatedTotalUseCase;

    @MockBean
    private GetCheckoutSessionUseCase getCheckoutSessionUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static final String TOKEN = "a-valid-looking-bearer-token";
    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    private final CustomerId customerId = CustomerId.generate();

    private void authenticateAsCustomer() {
        when(jwtTokenService.validateAndExtract(TOKEN)).thenReturn(new JwtClaims(UserId.of(customerId.value()), Role.CUSTOMER));
    }

    private static RecalculatedTotal totalOf(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        return new RecalculatedTotal(List.of(), money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private CheckoutSession pendingSession(CustomerId owner) {
        return CheckoutSession.start(CheckoutSessionId.generate(), owner, CartId.generate(), totalOf("50.00"),
                new Money(new BigDecimal("55.00"), "USD"), "idem-key-1", NOW);
    }

    private CheckoutSession awaitingPaymentSession(CustomerId owner) {
        CheckoutSession session = CheckoutSession.start(CheckoutSessionId.generate(), owner, CartId.generate(),
                totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "idem-key-1", NOW);
        session.moveToAwaitingPayment(ReservationId.generate(), NOW.plusSeconds(900));
        return session;
    }

    // ── POST /api/v1/checkout-sessions — auth decision table ─────────────────

    @Test
    void startCheckout_returns401_whenAnonymous() throws Exception {
        String body = """
                {"expectedTotal":{"amount":"50.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startCheckout_returns201WithLocationHeader_whenOutcomeConfirmed() throws Exception {
        authenticateAsCustomer();
        CheckoutSession confirmed = awaitingPaymentSession(customerId);
        when(startCheckoutUseCase.execute(any())).thenReturn(new CheckoutOutcome.Confirmed(confirmed));
        String body = """
                {"expectedTotal":{"amount":"50.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/checkout-sessions/" + confirmed.getId()))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.reservationId").value(confirmed.getReservationId().toString()));
    }

    @Test
    void startCheckout_returns200_whenOutcomeNeedsReconfirmation() throws Exception {
        authenticateAsCustomer();
        CheckoutSession pending = pendingSession(customerId);
        when(startCheckoutUseCase.execute(any())).thenReturn(new CheckoutOutcome.NeedsReconfirmation(pending));
        String body = """
                {"expectedTotal":{"amount":"55.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.reservationId").doesNotExist());
    }

    @Test
    void startCheckout_returns400_whenIdempotencyKeyHeaderMissing() throws Exception {
        authenticateAsCustomer();
        String body = """
                {"expectedTotal":{"amount":"50.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    @Test
    void startCheckout_returns400_whenExpectedTotalIsMissing() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(post("/api/v1/checkout-sessions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    // ── POST /api/v1/checkout-sessions/{id}/confirmation — auth decision table ──

    @Test
    void confirmRecalculatedTotal_returns401_whenAnonymous() throws Exception {
        String body = """
                {"confirmedTotal":{"amount":"50.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions/{id}/confirmation", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmRecalculatedTotal_returns201_whenOutcomeConfirmed() throws Exception {
        authenticateAsCustomer();
        CheckoutSession confirmed = awaitingPaymentSession(customerId);
        when(confirmRecalculatedTotalUseCase.execute(any())).thenReturn(new CheckoutOutcome.Confirmed(confirmed));
        String body = """
                {"confirmedTotal":{"amount":"50.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions/{id}/confirmation", confirmed.getId().value())
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }

    @Test
    void confirmRecalculatedTotal_returns404_whenSessionNotOwnedByCaller() throws Exception {
        authenticateAsCustomer();
        when(confirmRecalculatedTotalUseCase.execute(any()))
                .thenThrow(new CheckoutSessionNotFoundException("No checkout session for the current customer"));
        String body = """
                {"confirmedTotal":{"amount":"50.00","currency":"USD"}}
                """;

        mockMvc.perform(post("/api/v1/checkout-sessions/{id}/confirmation", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"));
    }

    @Test
    void confirmRecalculatedTotal_returns400_whenConfirmedTotalIsMissing() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(post("/api/v1/checkout-sessions/{id}/confirmation", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    // ── GET /api/v1/checkout-sessions/{id} — auth decision table ─────────────

    @Test
    void getCheckoutSession_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/checkout-sessions/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCheckoutSession_returns200_whenOwnedByCaller() throws Exception {
        authenticateAsCustomer();
        CheckoutSession session = pendingSession(customerId);
        when(getCheckoutSessionUseCase.execute(session.getId(), customerId)).thenReturn(session);

        mockMvc.perform(get("/api/v1/checkout-sessions/{id}", session.getId().value())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));
    }

    @Test
    void getCheckoutSession_returns404_whenNotOwnedByCallerOrMissing() throws Exception {
        authenticateAsCustomer();
        UUID id = UUID.randomUUID();
        when(getCheckoutSessionUseCase.execute(CheckoutSessionId.of(id), customerId))
                .thenThrow(new CheckoutSessionNotFoundException("No checkout session for the current customer"));

        mockMvc.perform(get("/api/v1/checkout-sessions/{id}", id)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getCheckoutSession_returns400_whenIdIsNotAValidUuid() throws Exception {
        authenticateAsCustomer();

        mockMvc.perform(get("/api/v1/checkout-sessions/{id}", "not-a-uuid")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }
}

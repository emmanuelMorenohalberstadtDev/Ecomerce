package com.ecommerce.cart.presentation;

import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.cart.application.usecase.AddItemUseCase;
import com.ecommerce.cart.application.usecase.AddItemUseCase.AddItemResult;
import com.ecommerce.cart.application.usecase.GetOrCreateCartUseCase;
import com.ecommerce.cart.application.usecase.MergeCartsUseCase;
import com.ecommerce.cart.application.usecase.RemoveLineUseCase;
import com.ecommerce.cart.application.usecase.UpdateLineQuantityUseCase;
import com.ecommerce.cart.application.usecase.ViewCartUseCase;
import com.ecommerce.cart.domain.exception.CartNotFoundException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.ProductSnapshot;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link CartController} using {@code @WebMvcTest}, matching the depth of
 * catalog's {@code ProductControllerTest}/{@code AdminProductControllerTest} — not exhaustive
 * MockMvc coverage of every endpoint/status combination (that is the use-case unit tests' job),
 * just the HTTP-contract surface: happy-path shape, guest-cookie issuance attributes
 * (security-architecture §2.7), and the not-found path surfacing as 404.
 *
 * <p><strong>{@code /api/v1/carts/**} is a public route</strong> ({@code permitAll()} in
 * {@code AuthSecurityConfiguration}) — GUEST is a real identity here (security §2.7, §3.4),
 * resolved from the guest cart cookie rather than a Bearer token, so a truly anonymous request
 * must reach {@link CartController}. This was fixed in {@code AuthSecurityConfiguration}: the
 * coarse {@code .anyRequest().authenticated()} rule excludes {@code AnonymousAuthenticationToken}
 * (confirmed against Spring Security 6.4.5 bytecode — {@code AuthenticatedAuthorizationManager}
 * requires both {@code isAuthenticated()} and {@code !isAnonymous(...)}), so without an explicit
 * {@code permitAll()} for the cart path, anonymous callers were rejected with 401 before ever
 * reaching this controller. Ownership (own cart only) is enforced inside the use cases via the
 * resolved identity; ADMIN gets 403 via {@code @PreAuthorize} on every cart use case (§3.2 fine
 * layer) — the route rule only widens who reaches the controller, not who succeeds.
 */
@Tag("unit")
@WebMvcTest(CartController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetOrCreateCartUseCase getOrCreateCartUseCase;

    @MockBean
    private ViewCartUseCase viewCartUseCase;

    @MockBean
    private AddItemUseCase addItemUseCase;

    @MockBean
    private UpdateLineQuantityUseCase updateLineQuantityUseCase;

    @MockBean
    private RemoveLineUseCase removeLineUseCase;

    @MockBean
    private MergeCartsUseCase mergeCartsUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static Cart cartWithOneLine() {
        Cart cart = Cart.createGuestCart("some-hash");
        cart.addItem(ProductId.generate(), new ProductSnapshot("Widget", new Money(new BigDecimal("19.99"), "USD")),
                Quantity.of(2), Instant.parse("2026-07-31T12:00:00Z"));
        return cart;
    }

    // ── truly anonymous callers reach the controller (permitAll route, see class javadoc) ──

    @Test
    void getMyCart_reachesController_whenCallerIsTrulyAnonymous() throws Exception {
        // No Authorization header, no guest cookie: resolveCustomerId() -> null, guestToken -> null.
        // The point of this test is that the request reaches CartController and gets a normal
        // business-logic 404 (no cart for this identity yet), NOT a 401 from the security filter
        // chain — that's the regression this test guards against.
        when(viewCartUseCase.execute(null, null))
                .thenThrow(new CartNotFoundException("No active cart found for the current identity"));

        mockMvc.perform(get("/api/v1/carts/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"));
    }

    // ── POST /api/v1/carts/me/items — happy path ──────────────────────────

    @Test
    @WithMockUser
    void addItem_returns200WithCartBody_whenCallerIsGuest() throws Exception {
        Cart cart = cartWithOneLine();
        when(addItemUseCase.execute(any())).thenReturn(new AddItemResult(cart, null));
        String body = """
                {"productId":"%s","quantity":2}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/carts/me/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cart.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value("19.99"))
                .andExpect(jsonPath("$.items[0].unitPrice.currency").value("USD"));
    }

    // ── guest-cookie issuance on first interaction (security-architecture §2.7) ──

    @Test
    @WithMockUser
    void addItem_issuesGuestCartCookie_withRequiredSecurityAttributes_whenNewGuestTokenMinted() throws Exception {
        Cart cart = cartWithOneLine();
        when(addItemUseCase.execute(any())).thenReturn(new AddItemResult(cart, "brand-new-raw-token"));
        String body = """
                {"productId":"%s","quantity":1}
                """.formatted(UUID.randomUUID());

        MvcResult result = mockMvc.perform(post("/api/v1/carts/me/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("guestCartToken=brand-new-raw-token");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("Secure");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/api/v1");
    }

    @Test
    @WithMockUser
    void addItem_doesNotSetCookie_whenGuestTokenAlreadyResolved() throws Exception {
        Cart cart = cartWithOneLine();
        when(addItemUseCase.execute(any())).thenReturn(new AddItemResult(cart, null));
        String body = """
                {"productId":"%s","quantity":1}
                """.formatted(UUID.randomUUID());

        MvcResult result = mockMvc.perform(post("/api/v1/carts/me/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(new jakarta.servlet.http.Cookie("guestCartToken", "already-known-token")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    // ── 404, not 500 — ownership/identity-resolution case ─────────────────

    @Test
    @WithMockUser
    void getMyCart_returns404_whenCallerIdentityDoesNotResolveToAnActiveCart() throws Exception {
        when(viewCartUseCase.execute(null, "someone-elses-or-unknown-token"))
                .thenThrow(new CartNotFoundException("No active cart found for the current identity"));

        mockMvc.perform(get("/api/v1/carts/me")
                        .cookie(new jakarta.servlet.http.Cookie("guestCartToken", "someone-elses-or-unknown-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── validation ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void addItem_returns400_whenQuantityExceedsMax() throws Exception {
        String body = """
                {"productId":"%s","quantity":100}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/carts/me/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

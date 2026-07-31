package com.ecommerce.catalog.presentation;

import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.catalog.application.usecase.ChangeProductPriceUseCase;
import com.ecommerce.catalog.application.usecase.CreateProductUseCase;
import com.ecommerce.catalog.application.usecase.RetireProductUseCase;
import com.ecommerce.catalog.application.usecase.UpdateProductUseCase;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link AdminProductController} using {@code @WebMvcTest}.
 *
 * <p>Covers the auth-decision-table contract for the admin-only surface (api-guidelines §7.3):
 * anonymous -> 401, wrong role (CUSTOMER) -> 403, correct role (ADMIN) -> 2xx. This exercises
 * the coarse route-layer check in {@code AuthSecurityConfiguration}
 * ({@code /api/v1/admin/**} requires {@code ADMIN}) — the fine-grained
 * {@code @PreAuthorize("hasRole('ADMIN')")} on the use case itself is not reachable from a
 * {@code @WebMvcTest} slice since the use case bean is replaced by a Mockito mock (no method
 * security proxy wraps a mock), so it is out of scope for this test tier.
 */
@Tag("unit")
@WebMvcTest(AdminProductController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateProductUseCase createProductUseCase;

    @MockBean
    private UpdateProductUseCase updateProductUseCase;

    @MockBean
    private ChangeProductPriceUseCase changeProductPriceUseCase;

    @MockBean
    private RetireProductUseCase retireProductUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static final String CREATE_BODY = """
            {"categoryId":"%s","sku":"abc-123","basePrice":{"amount":"19.99","currency":"USD"},
             "name":"Widget","description":"A widget"}
            """.formatted(UUID.randomUUID());

    // ── POST /api/v1/admin/products — auth decision table ─────────────────────

    @Test
    void create_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201_whenAdminRole() throws Exception {
        Product product = Product.create(CategoryId.generate(), new Sku("abc-123"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "A widget", List.of());
        when(createProductUseCase.execute(any())).thenReturn(product);

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns400_whenSkuIsBlank() throws Exception {
        String invalidBody = """
                {"categoryId":"%s","sku":"","basePrice":{"amount":"19.99","currency":"USD"},
                 "name":"Widget"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/admin/products/{id}/retirement — auth decision table ────

    @Test
    void retire_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products/{id}/retirement", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void retire_returns403_whenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products/{id}/retirement", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}

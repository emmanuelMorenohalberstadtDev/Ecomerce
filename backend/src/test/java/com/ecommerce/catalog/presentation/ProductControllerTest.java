package com.ecommerce.catalog.presentation;

import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import com.ecommerce.catalog.application.usecase.GetProductUseCase;
import com.ecommerce.catalog.application.usecase.SearchProductsUseCase;
import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link ProductController} using {@code @WebMvcTest}.
 *
 * <p>Both endpoints are public ({@code permitAll()} in {@code AuthSecurityConfiguration}) — no
 * authentication simulation needed here (contrast {@code AdminProductControllerTest}, which
 * covers the 401/403/2xx decision table for the admin-only surface).
 */
@Tag("unit")
@WebMvcTest(ProductController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchProductsUseCase searchProductsUseCase;

    @MockBean
    private GetProductUseCase getProductUseCase;

    // Required by JwtAuthenticationFilter wired in AuthSecurityConfiguration
    @MockBean
    private JwtTokenService jwtTokenService;

    private static Product sampleProduct() {
        return Product.create(CategoryId.generate(), new Sku("abc-123"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "desc", List.of());
    }

    // ── GET /api/v1/products ──────────────────────────────────────────────────

    @Test
    void search_returns200WithPageEnvelope_whenAnonymous() throws Exception {
        Product product = sampleProduct();
        when(searchProductsUseCase.execute(any()))
                .thenReturn(new PageResult<>(List.of(product), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Widget"))
                .andExpect(jsonPath("$.content[0].basePrice.amount").value("19.99"))
                .andExpect(jsonPath("$.content[0].basePrice.currency").value("USD"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    /**
     * An unallowlisted {@code sort} value is rejected by the {@code @Pattern}-annotated
     * {@code @RequestParam} (correct modeling per api-guidelines §4.4). {@code ApiExceptionHandler}
     * now maps the resulting {@code HandlerMethodValidationException} to 400, distinct from the
     * AOP-based {@code ConstraintViolationException} path (service-layer {@code @Validated}) and
     * from {@code MethodArgumentNotValidException} (body {@code @Valid} failures).
     */
    @Test
    void search_returns400_whenSortFieldIsNotAllowlisted() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "notarealfield"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }

    // ── GET /api/v1/products/{id} ──────────────────────────────────────────────

    @Test
    void getById_returns200_whenProductIsActive() throws Exception {
        Product product = sampleProduct();
        when(getProductUseCase.execute(product.getId().toString())).thenReturn(product);

        mockMvc.perform(get("/api/v1/products/{id}", product.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void getById_returns404_whenProductDoesNotExist() throws Exception {
        ProductId id = ProductId.generate();
        when(getProductUseCase.execute(id.toString()))
                .thenThrow(new ProductNotFoundException("Product not found: " + id));

        mockMvc.perform(get("/api/v1/products/{id}", id.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getById_returns400_whenIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"));
    }
}

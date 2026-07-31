package com.ecommerce.catalog.presentation;

import com.ecommerce.catalog.application.usecase.GetProductUseCase;
import com.ecommerce.catalog.application.usecase.SearchProductsUseCase;
import com.ecommerce.catalog.application.usecase.SearchProductsUseCase.SearchProductsQuery;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.domain.port.out.ProductSort;
import com.ecommerce.catalog.presentation.dto.PageResponse;
import com.ecommerce.catalog.presentation.dto.ProductResponse;
import com.ecommerce.catalog.presentation.mapper.ProductMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public read surface for products: {@code GET /api/v1/products} and
 * {@code GET /api/v1/products/{id}}. Both endpoints are {@code permitAll()} at the route layer
 * ({@code AuthSecurityConfiguration}) and show ACTIVE products only, regardless of caller role
 * (security-architecture §3.4 seed row: "Public catalog reads ... ACTIVE products only for
 * non-admin" — this controller has no admin-aware variant, so ADMIN callers see the same
 * ACTIVE-only view here too; there is no admin "browse including retired" endpoint in v1, see
 * the catalog handoff report).
 *
 * <p>All mutation endpoints live in {@link AdminProductController} under
 * {@code /api/v1/admin/products/**}.
 *
 * <p>Auth decision table:
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>GET /products</td><td>200</td><td>200</td><td>200</td>
 *       <td>ACTIVE only; no ownership</td></tr>
 *   <tr><td>GET /products/{id}</td><td>200 or 404</td><td>200 or 404</td><td>200 or 404</td>
 *       <td>RETIRED is 404 for every caller</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final SearchProductsUseCase searchProductsUseCase;
    private final GetProductUseCase getProductUseCase;

    public ProductController(SearchProductsUseCase searchProductsUseCase,
                             GetProductUseCase getProductUseCase) {
        this.searchProductsUseCase = searchProductsUseCase;
        this.getProductUseCase = getProductUseCase;
    }

    /**
     * Lists/searches ACTIVE products, optionally filtered by category and/or a case-insensitive
     * name prefix. Sort is allowlisted to {@code name} (default) and {@code basePrice}
     * (api-guidelines §4.4) — {@code idx_products_name_lower} and
     * {@code idx_products_category_active} back these respectively.
     */
    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false)
            @Pattern(regexp = UUID_PATTERN, message = "categoryId must be a valid UUID")
            String categoryId,
            @RequestParam(name = "name", required = false) String namePrefix,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "name") @Pattern(regexp = "name|basePrice") String sort,
            @RequestParam(defaultValue = "asc") @Pattern(regexp = "asc|desc") String direction) {

        ProductSort productSort = new ProductSort(
                "basePrice".equals(sort) ? ProductSort.Field.BASE_PRICE : ProductSort.Field.NAME,
                "desc".equals(direction));

        PageResult<Product> result = searchProductsUseCase.execute(
                new SearchProductsQuery(categoryId, namePrefix, productSort, page, size));

        return new PageResponse<>(
                result.content().stream().map(ProductMapper::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        Product product = getProductUseCase.execute(id.toString());
        return ProductMapper.toResponse(product);
    }
}

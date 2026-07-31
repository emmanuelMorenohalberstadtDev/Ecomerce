package com.ecommerce.catalog.presentation;

import com.ecommerce.catalog.application.usecase.GetCategoryUseCase;
import com.ecommerce.catalog.application.usecase.ListCategoriesUseCase;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.presentation.dto.CategoryResponse;
import com.ecommerce.catalog.presentation.dto.PageResponse;
import com.ecommerce.catalog.presentation.mapper.CategoryMapper;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public read surface for categories: {@code GET /api/v1/categories} and
 * {@code GET /api/v1/categories/{id}}. Both endpoints are {@code permitAll()} at the route layer
 * ({@code AuthSecurityConfiguration}). Categories carry no ACTIVE/RETIRED concept, so unlike
 * {@link ProductController} there is no status filtering here.
 *
 * <p>All mutation endpoints live in {@link AdminCategoryController} under
 * {@code /api/v1/admin/categories/**}.
 *
 * <p>Auth decision table:
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>GET /categories</td><td>200</td><td>200</td><td>200</td><td>no ownership</td></tr>
 *   <tr><td>GET /categories/{id}</td><td>200 or 404</td><td>200 or 404</td><td>200 or 404</td>
 *       <td>404 if id does not exist</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/categories")
@Validated
public class CategoryController {

    private final ListCategoriesUseCase listCategoriesUseCase;
    private final GetCategoryUseCase getCategoryUseCase;

    public CategoryController(ListCategoriesUseCase listCategoriesUseCase,
                              GetCategoryUseCase getCategoryUseCase) {
        this.listCategoriesUseCase = listCategoriesUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
    }

    @GetMapping
    public PageResponse<CategoryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {

        PageResult<Category> result = listCategoriesUseCase.execute(page, size);

        return new PageResponse<>(
                result.content().stream().map(CategoryMapper::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    public CategoryResponse getById(@PathVariable UUID id) {
        Category category = getCategoryUseCase.execute(id.toString());
        return CategoryMapper.toResponse(category);
    }
}

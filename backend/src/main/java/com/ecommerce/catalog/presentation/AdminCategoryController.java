package com.ecommerce.catalog.presentation;

import com.ecommerce.catalog.application.usecase.CreateCategoryUseCase;
import com.ecommerce.catalog.application.usecase.CreateCategoryUseCase.CreateCategoryCommand;
import com.ecommerce.catalog.application.usecase.DeleteCategoryUseCase;
import com.ecommerce.catalog.application.usecase.DeleteCategoryUseCase.DeleteCategoryCommand;
import com.ecommerce.catalog.application.usecase.UpdateCategoryUseCase;
import com.ecommerce.catalog.application.usecase.UpdateCategoryUseCase.UpdateCategoryCommand;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.presentation.dto.CategoryResponse;
import com.ecommerce.catalog.presentation.dto.CreateCategoryRequest;
import com.ecommerce.catalog.presentation.dto.UpdateCategoryRequest;
import com.ecommerce.catalog.presentation.mapper.CategoryMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Admin CRUD for categories: {@code /api/v1/admin/categories/**}. The route matcher in
 * {@code AuthSecurityConfiguration} already requires {@code ADMIN} at the coarse layer for
 * everything under {@code /api/v1/admin/**}; each underlying use case additionally carries
 * {@code @PreAuthorize("hasRole('ADMIN')")} as the fine-grained second layer
 * (security-architecture §3.2). Every mutation writes one row to the admin audit log
 * (security §6c) via {@code AuditLogPort}, in the same transaction as the mutation.
 *
 * <p>Auth decision table — identical for every endpoint in this controller (no ownership
 * dimension; admin ops are role-gated only, security-architecture §3.4):
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th></tr>
 *   <tr><td>POST /admin/categories</td><td>401</td><td>403</td><td>201</td></tr>
 *   <tr><td>PUT /admin/categories/{id}</td><td>401</td><td>403</td><td>200</td></tr>
 *   <tr><td>DELETE /admin/categories/{id}</td><td>401</td><td>403</td><td>204</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@Validated
public class AdminCategoryController {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final DeleteCategoryUseCase deleteCategoryUseCase;

    public AdminCategoryController(CreateCategoryUseCase createCategoryUseCase,
                                   UpdateCategoryUseCase updateCategoryUseCase,
                                   DeleteCategoryUseCase deleteCategoryUseCase) {
        this.createCategoryUseCase = createCategoryUseCase;
        this.updateCategoryUseCase = updateCategoryUseCase;
        this.deleteCategoryUseCase = deleteCategoryUseCase;
    }

    /**
     * @return 201 Created, {@code Location} pointing at the public read endpoint
     *         ({@code GET /api/v1/categories/{id}} — there is no admin-specific read endpoint).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CategoryResponse> create(@RequestBody @Valid CreateCategoryRequest request) {
        Category category = createCategoryUseCase.execute(
                new CreateCategoryCommand(request.name(), request.parentId()));

        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.getId()))
                .body(CategoryMapper.toResponse(category));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateCategoryRequest request) {
        Category category = updateCategoryUseCase.execute(
                new UpdateCategoryCommand(id.toString(), request.name(), request.parentId()));
        return CategoryMapper.toResponse(category);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteCategoryUseCase.execute(new DeleteCategoryCommand(id.toString()));
    }
}

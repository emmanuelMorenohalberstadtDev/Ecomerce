package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.exception.CategoryNotEmptyException;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * Deletes a category. Admin-only mutation. Unlike {@code Product}, categories have no
 * ACTIVE/RETIRED status — deletion (not retirement) is the only removal path, but only for an
 * empty category (no child categories, no products referencing it).
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx (route layer +
 * {@link PreAuthorize} defense-in-depth, security-architecture §3.2).
 *
 * <p>Pre-checks {@link CategoryRepository#hasChildren} and
 * {@link ProductRepository#existsByCategoryId} before deleting
 * ({@link CategoryNotEmptyException}, 409) — the DB's {@code ON DELETE RESTRICT} FKs are the
 * backstop for the race between this check and the delete (translated by the JPA adapter, not
 * leaked as a raw constraint violation).
 *
 * <p>Transaction boundary is this use case class; the audit row is written in the same
 * transaction as the delete (security §6c requirement 3).
 */
@Service
@Transactional
public class DeleteCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuditLogPort auditLogPort;

    public DeleteCategoryUseCase(CategoryRepository categoryRepository,
                                 ProductRepository productRepository,
                                 AuditLogPort auditLogPort) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.productRepository = Objects.requireNonNull(productRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void execute(DeleteCategoryCommand command) {
        CategoryId id = CategoryId.of(command.categoryId());
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));

        if (categoryRepository.hasChildren(id) || productRepository.existsByCategoryId(id)) {
            throw new CategoryNotEmptyException(
                    "Category " + id + " still has child categories or products and cannot be deleted");
        }

        categoryRepository.delete(category);

        auditLogPort.record(CatalogAuditAction.CATEGORY_DELETED, "Category", id.toString(),
                Map.of("name", category.getName()));
    }

    public record DeleteCategoryCommand(String categoryId) {}
}

package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates a new category, optionally under an existing parent. Admin-only mutation.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx (route layer +
 * {@link PreAuthorize} defense-in-depth, security-architecture §3.2).
 *
 * <p>Transaction boundary is this use case class; the audit row is written in the same
 * transaction as the insert (security §6c requirement 3).
 */
@Service
@Transactional
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final AuditLogPort auditLogPort;

    public CreateCategoryUseCase(CategoryRepository categoryRepository, AuditLogPort auditLogPort) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Category execute(CreateCategoryCommand command) {
        CategoryId parentId = null;
        if (command.parentId() != null) {
            parentId = CategoryId.of(command.parentId());
            if (!categoryRepository.existsById(parentId)) {
                throw new CategoryNotFoundException("Parent category not found: " + parentId);
            }
        }

        Category category = Category.create(command.name(), parentId);
        Category saved = categoryRepository.save(category);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", saved.getName());
        details.put("parentId", saved.getParentId() == null ? null : saved.getParentId().toString());
        auditLogPort.record(CatalogAuditAction.CATEGORY_CREATED, "Category",
                saved.getId().toString(), details);

        return saved;
    }

    /** @param parentId nullable — {@code null} creates a root category */
    public record CreateCategoryCommand(String name, String parentId) {}
}

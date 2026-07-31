package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.exception.CategoryCycleException;
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
 * Updates a category's name and/or parent. Admin-only mutation.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx (route layer +
 * {@link PreAuthorize} defense-in-depth, security-architecture §3.2).
 *
 * <p>{@link Category#updateDetails} only guards the trivial self-parent case; this use case
 * additionally walks the tree via {@link CategoryRepository} to reject re-parenting a category
 * under one of its own descendants ({@link CategoryCycleException}, 422) — a full-tree
 * invariant no single aggregate instance can see on its own. The walk is bounded
 * ({@link #MAX_DEPTH}) as a defensive guard against an already-corrupt tree looping forever;
 * in a healthy tree it terminates at the root long before the bound is hit.
 *
 * <p>Transaction boundary is this use case class; the audit row is written in the same
 * transaction as the update (security §6c requirement 3).
 */
@Service
@Transactional
public class UpdateCategoryUseCase {

    /** Defensive bound on ancestor-chain walk depth; a healthy tree never approaches this. */
    private static final int MAX_DEPTH = 1000;

    private final CategoryRepository categoryRepository;
    private final AuditLogPort auditLogPort;

    public UpdateCategoryUseCase(CategoryRepository categoryRepository, AuditLogPort auditLogPort) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Category execute(UpdateCategoryCommand command) {
        CategoryId id = CategoryId.of(command.categoryId());
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));

        CategoryId parentId = null;
        if (command.parentId() != null) {
            parentId = CategoryId.of(command.parentId());
            if (!categoryRepository.existsById(parentId)) {
                throw new CategoryNotFoundException("Parent category not found: " + parentId);
            }
            rejectCycle(id, parentId);
        }

        category.updateDetails(command.name(), parentId);
        Category saved = categoryRepository.save(category);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", saved.getName());
        details.put("parentId", saved.getParentId() == null ? null : saved.getParentId().toString());
        auditLogPort.record(CatalogAuditAction.CATEGORY_UPDATED, "Category",
                saved.getId().toString(), details);

        return saved;
    }

    private void rejectCycle(CategoryId categoryBeingUpdated, CategoryId newParentId) {
        CategoryId cursor = newParentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(categoryBeingUpdated)) {
                throw new CategoryCycleException(
                        "Cannot re-parent category " + categoryBeingUpdated
                                + " under its own descendant " + newParentId);
            }
            if (++depth > MAX_DEPTH) {
                throw new IllegalStateException(
                        "Category ancestor chain exceeded " + MAX_DEPTH
                                + " levels while checking for cycles — possible pre-existing data corruption");
            }
            cursor = categoryRepository.findById(cursor).map(Category::getParentId).orElse(null);
        }
    }

    /** @param parentId nullable — {@code null} makes the category a root category */
    public record UpdateCategoryCommand(String categoryId, String name, String parentId) {}
}

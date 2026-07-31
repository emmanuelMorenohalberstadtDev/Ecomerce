package com.ecommerce.catalog.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when an admin attempts to re-parent a category under one of its own descendants,
 * which would turn the category tree into a cycle.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler} — the request is well-formed (both
 * category ids exist) but the domain rule "the category tree is acyclic" rejects it.
 *
 * <p>Detecting this requires walking the tree via {@code CategoryRepository}, so it is raised
 * by {@code UpdateCategoryUseCase} rather than by {@link com.ecommerce.catalog.domain.model.Category}
 * itself, which only guards the trivial self-parent case (see {@code Category#updateDetails}).
 */
public final class CategoryCycleException extends BusinessRuleViolationException {

    public CategoryCycleException(String message) {
        super(message);
    }
}

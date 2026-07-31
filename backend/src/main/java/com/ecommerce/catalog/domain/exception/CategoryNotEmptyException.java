package com.ecommerce.catalog.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when an admin attempts to delete a category that still has child categories or
 * products referencing it.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler} — the category exists but the requested
 * mutation (delete) is incompatible with its current state (it is not empty).
 *
 * <p>Raised in two layers, defense-in-depth: (1) {@code DeleteCategoryUseCase} pre-checks
 * {@code CategoryRepository.hasChildren} and {@code ProductRepository.existsByCategoryId}
 * before attempting the delete; (2) the JPA adapter translates a raw
 * {@code fk_categories_parent} / {@code fk_products_category} {@code RESTRICT} violation into
 * this exception as a backstop against the race between the pre-check and the delete (e.g. a
 * product created in the same category concurrently with the delete request), rather than
 * letting a raw {@code DataIntegrityViolationException} leak to the client.
 */
public final class CategoryNotEmptyException extends ConflictException {

    public CategoryNotEmptyException(String message) {
        super(message);
    }

    public CategoryNotEmptyException(String message, Throwable cause) {
        super(message, cause);
    }
}

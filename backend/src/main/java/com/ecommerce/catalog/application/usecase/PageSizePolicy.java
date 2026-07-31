package com.ecommerce.catalog.application.usecase;

/**
 * Shared page/size validation-and-clamp policy for catalog's paginated list use cases
 * (api-guidelines.md §4.1–§4.2).
 *
 * <p>Not a general-purpose {@code Utils} grab bag — one narrow, named responsibility (the
 * pagination bound-checking rule), reused by {@link SearchProductsUseCase} and
 * {@link ListCategoriesUseCase} so the clamp behavior cannot drift between the two.
 */
final class PageSizePolicy {

    static final int MAX_SIZE = 100;

    private PageSizePolicy() {}

    /**
     * @throws IllegalArgumentException if {@code page} is negative
     */
    static int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got: " + page);
        }
        return page;
    }

    /**
     * Clamps {@code size} to {@value #MAX_SIZE} when it exceeds the hard cap — the single
     * sanctioned "clamp, don't reject" exception in the validation rules (api-guidelines §4.2).
     * Zero/negative values are still rejected, never silently corrected.
     *
     * @throws IllegalArgumentException if {@code size} is zero or negative
     */
    static int clampSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be >= 1, got: " + size);
        }
        return Math.min(size, MAX_SIZE);
    }
}

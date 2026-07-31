package com.ecommerce.catalog.domain.port.out;

/**
 * Framework-free sort specification for {@link ProductRepository} listing/search methods.
 *
 * <p>{@code field} is restricted to the allowlisted sort keys documented on
 * {@code SearchProductsUseCase} (api-guidelines.md §4.4: sort fields are allowlisted per
 * endpoint; a value outside the allowlist is rejected with 400 before it ever reaches this
 * type) — the two columns actually indexed for this purpose
 * ({@code idx_products_category_active} covers {@code basePrice} within a category,
 * {@code idx_products_name_lower} covers {@code name}).
 */
public record ProductSort(Field field, boolean descending) {

    public enum Field {
        NAME,
        BASE_PRICE
    }

    public ProductSort {
        if (field == null) throw new IllegalArgumentException("field must not be null");
    }

    public static ProductSort defaultSort() {
        return new ProductSort(Field.NAME, false);
    }
}

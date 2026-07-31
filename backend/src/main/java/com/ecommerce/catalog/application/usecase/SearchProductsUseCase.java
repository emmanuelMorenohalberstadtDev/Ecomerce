package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.catalog.domain.port.out.ProductSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Public, paginated product listing/search. Serves {@code GET /api/v1/products}, with or
 * without filters — consolidated into one use case (rather than a separate
 * {@code ListProductsUseCase}) because "list all" is simply "search with no filters applied";
 * splitting them would duplicate the pagination/ACTIVE-only/sort-allowlist logic across two
 * classes for no behavioral difference.
 *
 * <p>Auth decision table row: GUEST — 200 (ACTIVE only); CUSTOMER — 200 (ACTIVE only);
 * ADMIN — 200 (ACTIVE only) — security-architecture §3.4 seed row.
 *
 * <p>Sort fields are allowlisted to {@code name} and {@code basePrice}
 * (api-guidelines §4.4) — matches {@link ProductSort.Field}; there is no way to construct this
 * use case's command with an arbitrary client-supplied sort string, by construction (the
 * controller/mapper is responsible for rejecting an unrecognized sort key with 400 before
 * building {@link ProductSort}).
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class SearchProductsUseCase {

    private final ProductRepository productRepository;

    public SearchProductsUseCase(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository);
    }

    public PageResult<Product> execute(SearchProductsQuery query) {
        int page = PageSizePolicy.validatePage(query.page());
        int size = PageSizePolicy.clampSize(query.size());

        CategoryId categoryId = query.categoryId() == null ? null : CategoryId.of(query.categoryId());
        String namePrefixLower = query.namePrefix() == null || query.namePrefix().isBlank()
                ? null
                : query.namePrefix().trim().toLowerCase();
        ProductSort sort = query.sort() == null ? ProductSort.defaultSort() : query.sort();

        return productRepository.searchActive(categoryId, namePrefixLower, sort, page, size);
    }

    /**
     * @param categoryId  nullable — UUID string of the category filter
     * @param namePrefix  nullable — case-insensitive name-prefix filter
     * @param sort        nullable — defaults to {@link ProductSort#defaultSort()}
     * @param page        zero-based page index
     * @param size        requested page size (clamped to {@link PageSizePolicy#MAX_SIZE})
     */
    public record SearchProductsQuery(String categoryId, String namePrefix, ProductSort sort,
                                      int page, int size) {}
}

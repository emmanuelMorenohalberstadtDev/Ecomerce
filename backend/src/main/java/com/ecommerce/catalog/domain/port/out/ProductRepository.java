package com.ecommerce.catalog.domain.port.out;

import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.shared.id.ProductId;

import java.util.Optional;

/**
 * Domain port (outbound) for {@link Product} persistence.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaProductRepository} in the
 * infrastructure layer. The domain never references infrastructure types. One repository per
 * aggregate root (domain-model.md §2 ownership rules) — no separate repository for
 * {@code ProductImage}, which is persisted as part of the {@code Product} aggregate.
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and the ArchUnit rule
 * {@code repository_interfaces_live_in_domain}.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(ProductId id);

    Optional<Product> findBySku(Sku sku);

    boolean existsBySku(Sku sku);

    /** Used by {@code DeleteCategoryUseCase} to guard against deleting a non-empty category. */
    boolean existsByCategoryId(CategoryId categoryId);

    /**
     * ACTIVE-only paginated search, matching either or both of the two nullable filters.
     *
     * @param categoryId    nullable — when present, filters to that category
     *                      ({@code idx_products_category_active})
     * @param namePrefixLower nullable — when present, matches products whose
     *                      {@code lower(name)} starts with this (already-lowercased) prefix
     *                      ({@code idx_products_name_lower})
     * @param sort          allowlisted sort field/direction (never a raw client-supplied string)
     * @param page          zero-based page index
     * @param size          page size, already clamped/validated by the caller
     */
    PageResult<Product> searchActive(CategoryId categoryId, String namePrefixLower,
                                     ProductSort sort, int page, int size);
}

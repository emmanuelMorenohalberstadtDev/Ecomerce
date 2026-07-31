package com.ecommerce.catalog.domain.port.out;

import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;

import java.util.Optional;

/**
 * Domain port (outbound) for {@link Category} persistence.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaCategoryRepository} in the
 * infrastructure layer. The domain never references infrastructure types.
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and the ArchUnit rule
 * {@code repository_interfaces_live_in_domain}.
 */
public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(CategoryId id);

    boolean existsById(CategoryId id);

    /** Used by {@code DeleteCategoryUseCase} to guard against deleting a non-empty category. */
    boolean hasChildren(CategoryId id);

    PageResult<Category> findAll(int page, int size);

    void delete(Category category);
}

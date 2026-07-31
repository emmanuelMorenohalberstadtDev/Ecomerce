package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Fetches one category by id. Public endpoint — categories have no ACTIVE/RETIRED concept, so
 * unlike {@link GetProductUseCase} there is no status filtering here.
 *
 * <p>Auth decision table row: GUEST — 200; CUSTOMER — 200; ADMIN — 200
 * (security-architecture §3.4 seed row: public catalog reads, no ownership).
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class GetCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public GetCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
    }

    public Category execute(String categoryIdRaw) {
        CategoryId id = CategoryId.of(categoryIdRaw);
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));
    }
}

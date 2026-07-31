package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Public, paginated category listing. Serves {@code GET /api/v1/categories}.
 *
 * <p>Auth decision table row: GUEST — 200; CUSTOMER — 200; ADMIN — 200
 * (security-architecture §3.4 seed row: public catalog reads, no ownership).
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class ListCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    public ListCategoriesUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
    }

    public PageResult<Category> execute(int page, int size) {
        int validPage = PageSizePolicy.validatePage(page);
        int validSize = PageSizePolicy.clampSize(size);
        return categoryRepository.findAll(validPage, validSize);
    }
}

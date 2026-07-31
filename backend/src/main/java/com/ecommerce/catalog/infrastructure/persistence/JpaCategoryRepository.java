package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.catalog.domain.exception.CategoryNotEmptyException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link CategoryRepository} port.
 *
 * <p>Maps between {@link Category} (domain) and {@link CategoryJpaEntity} (JPA). The domain
 * object never touches JPA internals; the JPA entity never escapes this adapter.
 */
public class JpaCategoryRepository implements CategoryRepository {

    private final SpringDataCategoryDao springDataCategoryDao;
    private final Clock clock;

    public JpaCategoryRepository(SpringDataCategoryDao springDataCategoryDao, Clock clock) {
        this.springDataCategoryDao = springDataCategoryDao;
        this.clock = clock;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity = toEntity(category);
        CategoryJpaEntity saved = springDataCategoryDao.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return springDataCategoryDao.findById(id.value()).map(this::toDomain);
    }

    @Override
    public boolean existsById(CategoryId id) {
        return springDataCategoryDao.existsById(id.value());
    }

    @Override
    public boolean hasChildren(CategoryId id) {
        return springDataCategoryDao.existsByParentId(id.value());
    }

    @Override
    public PageResult<Category> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<CategoryJpaEntity> result = springDataCategoryDao.findAll(pageable);

        return new PageResult<>(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public void delete(Category category) {
        try {
            springDataCategoryDao.deleteById(category.getId().value());
            springDataCategoryDao.flush();
        } catch (DataIntegrityViolationException e) {
            // Backstop for the race between DeleteCategoryUseCase's hasChildren/existsByCategoryId
            // pre-checks and this delete — translates the raw RESTRICT FK violation
            // (fk_categories_parent / fk_products_category) instead of leaking it.
            throw new CategoryNotEmptyException(
                    "Category " + category.getId()
                            + " still has child categories or products and cannot be deleted", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private CategoryJpaEntity toEntity(Category category) {
        Instant now = clock.instant();
        return new CategoryJpaEntity(
                category.getId().value(),
                category.getParentId() == null ? null : category.getParentId().value(),
                category.getName(),
                now // createdAt: ignored on UPDATE because updatable=false
        );
    }

    private Category toDomain(CategoryJpaEntity entity) {
        return Category.reconstitute(
                CategoryId.of(entity.getId()),
                entity.getParentId() == null ? null : CategoryId.of(entity.getParentId()),
                entity.getName()
        );
    }
}

package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.auth.infrastructure.persistence.PostgresIntegrationTestBase;
import com.ecommerce.catalog.domain.exception.CategoryNotEmptyException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link JpaCategoryRepository} against a real PostgreSQL instance via
 * Testcontainers.
 *
 * <p>Flyway applies V001+V002 migrations before the first test. {@code @Transactional} rolls
 * back after each test — no manual cleanup required.
 *
 * <p>Tests verify JPA + PostgreSQL behavior that cannot be tested with mocks: the
 * {@code fk_categories_parent}/{@code fk_products_category} {@code ON DELETE RESTRICT}
 * translation into {@link CategoryNotEmptyException}, and parent/child tree relationships.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@Transactional
class CategoryRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    // ── save + findById roundtrip ──────────────────────────────────────────────

    @Test
    void shouldFindCategoryById_afterSaving() {
        Category category = Category.create("Electronics", null);

        categoryRepository.save(category);
        Optional<Category> found = categoryRepository.findById(category.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Electronics");
        assertThat(found.get().isRoot()).isTrue();
    }

    @Test
    void shouldPersistParentChildRelationship() {
        Category parent = categoryRepository.save(Category.create("Electronics", null));
        Category child = categoryRepository.save(Category.create("Laptops", parent.getId()));

        Optional<Category> found = categoryRepository.findById(child.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getParentId()).isEqualTo(parent.getId());
    }

    @Test
    void existsById_returnsFalse_whenCategoryDoesNotExist() {
        assertThat(categoryRepository.existsById(CategoryId.generate())).isFalse();
    }

    // ── hasChildren ────────────────────────────────────────────────────────────

    @Test
    void hasChildren_returnsTrue_whenCategoryHasAChild() {
        Category parent = categoryRepository.save(Category.create("Electronics", null));
        categoryRepository.save(Category.create("Laptops", parent.getId()));

        assertThat(categoryRepository.hasChildren(parent.getId())).isTrue();
    }

    @Test
    void hasChildren_returnsFalse_whenCategoryHasNoChildren() {
        Category leaf = categoryRepository.save(Category.create("Laptops", null));

        assertThat(categoryRepository.hasChildren(leaf.getId())).isFalse();
    }

    // ── findAll pagination ─────────────────────────────────────────────────────

    @Test
    void findAll_returnsSavedCategories_sortedByName() {
        categoryRepository.save(Category.create("Zebra", null));
        categoryRepository.save(Category.create("Apple", null));

        PageResult<Category> result = categoryRepository.findAll(0, 20);

        assertThat(result.content()).extracting(Category::getName).containsExactly("Apple", "Zebra");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    // ── RESTRICT FK translation ────────────────────────────────────────────────

    @Test
    void delete_throwsCategoryNotEmptyException_whenCategoryHasChildren() {
        Category parent = categoryRepository.save(Category.create("Electronics", null));
        categoryRepository.save(Category.create("Laptops", parent.getId()));

        assertThatThrownBy(() -> categoryRepository.delete(parent))
                .isInstanceOf(CategoryNotEmptyException.class);
    }

    @Test
    void delete_throwsCategoryNotEmptyException_whenCategoryHasProducts() {
        Category category = categoryRepository.save(Category.create("Electronics", null));
        productRepository.save(Product.create(category.getId(), new Sku("sku-restrict"),
                new Money(new BigDecimal("1.00"), "USD"), "Widget", null, List.of()));

        assertThatThrownBy(() -> categoryRepository.delete(category))
                .isInstanceOf(CategoryNotEmptyException.class);
    }

    @Test
    void delete_succeeds_whenCategoryIsEmpty() {
        Category category = categoryRepository.save(Category.create("Empty", null));

        categoryRepository.delete(category);

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }
}

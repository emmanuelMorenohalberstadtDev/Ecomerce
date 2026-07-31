package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.auth.infrastructure.persistence.PostgresIntegrationTestBase;
import com.ecommerce.catalog.domain.exception.DuplicateSkuException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.ProductImage;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.catalog.domain.port.out.ProductSort;
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
 * Integration tests for {@link JpaProductRepository} against a real PostgreSQL instance via
 * Testcontainers.
 *
 * <p>Flyway applies V001+V002 migrations before the first test. {@code @Transactional} rolls
 * back after each test — no manual cleanup required.
 *
 * <p>Tests verify JPA + PostgreSQL behavior that cannot be tested with mocks: the
 * {@code uq_products_sku} unique constraint translation, ACTIVE/RETIRED status filtering via
 * {@code idx_products_category_active}, category-prefix search via
 * {@code idx_products_name_lower}, and ordered image persistence.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@Transactional
class ProductRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private CategoryId newSavedCategory(String name) {
        Category category = Category.create(name, null);
        return categoryRepository.save(category).getId();
    }

    // ── save + findById / findBySku roundtrip ─────────────────────────────────

    @Test
    void shouldFindProductById_afterSaving() {
        CategoryId categoryId = newSavedCategory("Electronics");
        Product product = Product.create(categoryId, new Sku("sku-001"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "A widget", List.of());

        productRepository.save(product);
        Optional<Product> found = productRepository.findById(product.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Widget");
        assertThat(found.get().getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void shouldFindProductBySku_afterSaving() {
        CategoryId categoryId = newSavedCategory("Electronics");
        Product product = Product.create(categoryId, new Sku("sku-002"),
                new Money(new BigDecimal("9.99"), "USD"), "Gadget", null, List.of());

        productRepository.save(product);
        Optional<Product> found = productRepository.findBySku(new Sku("sku-002"));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(product.getId());
    }

    @Test
    void existsBySku_returnsTrue_whenSkuIsTaken() {
        CategoryId categoryId = newSavedCategory("Electronics");
        productRepository.save(Product.create(categoryId, new Sku("sku-003"),
                new Money(new BigDecimal("1.00"), "USD"), "Item", null, List.of()));

        assertThat(productRepository.existsBySku(new Sku("sku-003"))).isTrue();
        assertThat(productRepository.existsBySku(new Sku("sku-does-not-exist"))).isFalse();
    }

    // ── unique SKU constraint ──────────────────────────────────────────────────

    @Test
    void save_throwsDuplicateSkuException_whenSkuAlreadyExistsForAnotherProduct() {
        CategoryId categoryId = newSavedCategory("Electronics");
        productRepository.save(Product.create(categoryId, new Sku("sku-dup"),
                new Money(new BigDecimal("1.00"), "USD"), "First", null, List.of()));

        Product second = Product.create(categoryId, new Sku("sku-dup"),
                new Money(new BigDecimal("2.00"), "USD"), "Second", null, List.of());

        assertThatThrownBy(() -> productRepository.save(second))
                .isInstanceOf(DuplicateSkuException.class);
    }

    // ── status filtering (ACTIVE vs RETIRED) ──────────────────────────────────

    @Test
    void searchActive_excludesRetiredProducts() {
        CategoryId categoryId = newSavedCategory("Electronics");
        Product active = Product.create(categoryId, new Sku("sku-active"),
                new Money(new BigDecimal("1.00"), "USD"), "Active Item", null, List.of());
        Product retired = Product.create(categoryId, new Sku("sku-retired"),
                new Money(new BigDecimal("1.00"), "USD"), "Retired Item", null, List.of());
        retired.retire();

        productRepository.save(active);
        productRepository.save(retired);

        PageResult<Product> result = productRepository.searchActive(
                categoryId, null, ProductSort.defaultSort(), 0, 20);

        assertThat(result.content()).extracting(Product::getName).containsExactly("Active Item");
    }

    @Test
    void searchActive_filtersByCategory() {
        CategoryId categoryA = newSavedCategory("Category A");
        CategoryId categoryB = newSavedCategory("Category B");
        productRepository.save(Product.create(categoryA, new Sku("sku-a"),
                new Money(new BigDecimal("1.00"), "USD"), "In A", null, List.of()));
        productRepository.save(Product.create(categoryB, new Sku("sku-b"),
                new Money(new BigDecimal("1.00"), "USD"), "In B", null, List.of()));

        PageResult<Product> result = productRepository.searchActive(
                categoryA, null, ProductSort.defaultSort(), 0, 20);

        assertThat(result.content()).extracting(Product::getName).containsExactly("In A");
    }

    @Test
    void searchActive_filtersByNamePrefix_caseInsensitive() {
        CategoryId categoryId = newSavedCategory("Electronics");
        productRepository.save(Product.create(categoryId, new Sku("sku-widget"),
                new Money(new BigDecimal("1.00"), "USD"), "Widget Pro", null, List.of()));
        productRepository.save(Product.create(categoryId, new Sku("sku-gadget"),
                new Money(new BigDecimal("1.00"), "USD"), "Gadget Pro", null, List.of()));

        PageResult<Product> result = productRepository.searchActive(
                null, "widget", ProductSort.defaultSort(), 0, 20);

        assertThat(result.content()).extracting(Product::getName).containsExactly("Widget Pro");
    }

    // ── images persisted in order ──────────────────────────────────────────────

    @Test
    void shouldPersistImages_inDisplayOrder() {
        CategoryId categoryId = newSavedCategory("Electronics");
        List<ProductImage> images = List.of(
                new ProductImage("https://example.com/0.jpg", 0),
                new ProductImage("https://example.com/1.jpg", 1));
        Product product = Product.create(categoryId, new Sku("sku-images"),
                new Money(new BigDecimal("1.00"), "USD"), "With Images", null, images);

        productRepository.save(product);
        Optional<Product> found = productRepository.findById(product.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getImages()).containsExactly(
                new ProductImage("https://example.com/0.jpg", 0),
                new ProductImage("https://example.com/1.jpg", 1));
    }

    // ── existsByCategoryId (used by DeleteCategoryUseCase) ────────────────────

    @Test
    void existsByCategoryId_returnsTrue_whenCategoryHasProducts() {
        CategoryId categoryId = newSavedCategory("Electronics");
        productRepository.save(Product.create(categoryId, new Sku("sku-cat-check"),
                new Money(new BigDecimal("1.00"), "USD"), "Item", null, List.of()));

        assertThat(productRepository.existsByCategoryId(categoryId)).isTrue();
    }

    @Test
    void existsByCategoryId_returnsFalse_whenCategoryHasNoProducts() {
        CategoryId categoryId = newSavedCategory("Empty Category");

        assertThat(productRepository.existsByCategoryId(categoryId)).isFalse();
    }
}

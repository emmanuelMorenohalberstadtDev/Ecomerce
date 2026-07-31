package com.ecommerce.catalog.domain.model;

import com.ecommerce.catalog.domain.exception.ProductAlreadyRetiredException;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Product} aggregate root.
 *
 * <p>No Spring context — pure domain logic via the factory and domain methods.
 */
@Tag("unit")
class ProductTest {

    private static final CategoryId CATEGORY_ID = CategoryId.generate();
    private static final Sku SKU = new Sku("ABC-123");
    private static final Money PRICE = new Money(new BigDecimal("19.99"), "USD");

    // ── create() factory ───────────────────────────────────────────────────────

    @Test
    void create_startsActive() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "A widget", List.of());

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void create_generatesNonNullId() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "A widget", List.of());

        assertThat(product.getId()).isNotNull();
        assertThat(product.getId().value()).isNotNull();
    }

    @Test
    void create_storesAllFields() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "A widget", List.of());

        assertThat(product.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(product.getSku()).isEqualTo(SKU);
        assertThat(product.getBasePrice()).isEqualTo(PRICE);
        assertThat(product.getName()).isEqualTo("Widget");
        assertThat(product.getDescription()).isEqualTo("A widget");
    }

    @Test
    void create_throwsIllegalArgumentException_whenNameIsBlank() {
        assertThatThrownBy(() -> Product.create(CATEGORY_ID, SKU, PRICE, "  ", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsDuplicateDisplayOrderAcrossImages() {
        List<ProductImage> images = List.of(
                new ProductImage("https://example.com/1.jpg", 0),
                new ProductImage("https://example.com/2.jpg", 0));

        assertThatThrownBy(() -> Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", images))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_acceptsOrderedDistinctImages() {
        List<ProductImage> images = List.of(
                new ProductImage("https://example.com/1.jpg", 0),
                new ProductImage("https://example.com/2.jpg", 1));

        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", images);

        assertThat(product.getImages()).hasSize(2);
    }

    // ── updateDetails() ────────────────────────────────────────────────────────

    @Test
    void updateDetails_changesNameDescriptionCategoryAndImages() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());
        CategoryId newCategory = CategoryId.generate();
        List<ProductImage> newImages = List.of(new ProductImage("https://example.com/new.jpg", 0));

        product.updateDetails(newCategory, "New Name", "New description", newImages);

        assertThat(product.getCategoryId()).isEqualTo(newCategory);
        assertThat(product.getName()).isEqualTo("New Name");
        assertThat(product.getDescription()).isEqualTo("New description");
        assertThat(product.getImages()).containsExactly(new ProductImage("https://example.com/new.jpg", 0));
    }

    @Test
    void updateDetails_doesNotChangeSkuOrPriceOrStatus() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());

        product.updateDetails(CATEGORY_ID, "New Name", "desc", List.of());

        assertThat(product.getSku()).isEqualTo(SKU);
        assertThat(product.getBasePrice()).isEqualTo(PRICE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void updateDetails_throwsIllegalArgumentException_whenNameIsBlank() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());

        assertThatThrownBy(() -> product.updateDetails(CATEGORY_ID, "", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── changePrice() ──────────────────────────────────────────────────────────

    @Test
    void changePrice_updatesBasePrice() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());
        Money newPrice = new Money(new BigDecimal("24.99"), "USD");

        product.changePrice(newPrice);

        assertThat(product.getBasePrice()).isEqualTo(newPrice);
    }

    @Test
    void changePrice_throwsIllegalArgumentException_whenNewPriceIsNull() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());

        assertThatThrownBy(() -> product.changePrice(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── retire() ───────────────────────────────────────────────────────────────

    @Test
    void retire_setsStatusToRetired() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());

        product.retire();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.RETIRED);
        assertThat(product.isActive()).isFalse();
    }

    @Test
    void retire_throwsProductAlreadyRetiredException_whenAlreadyRetired() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc", List.of());
        product.retire();

        assertThatThrownBy(product::retire)
                .isInstanceOf(ProductAlreadyRetiredException.class);
    }

    // ── reconstitute() ─────────────────────────────────────────────────────────

    @Test
    void reconstitute_restoresExactState() {
        var id = com.ecommerce.shared.id.ProductId.generate();
        Product product = Product.reconstitute(id, CATEGORY_ID, SKU, ProductStatus.RETIRED, PRICE,
                "Widget", "desc", List.of());

        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RETIRED);
        assertThat(product.isActive()).isFalse();
    }

    // ── images defensive copy ──────────────────────────────────────────────────

    @Test
    void getImages_returnsUnmodifiableList() {
        Product product = Product.create(CATEGORY_ID, SKU, PRICE, "Widget", "desc",
                List.of(new ProductImage("https://example.com/1.jpg", 0)));

        assertThatThrownBy(() -> product.getImages().add(new ProductImage("https://example.com/2.jpg", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

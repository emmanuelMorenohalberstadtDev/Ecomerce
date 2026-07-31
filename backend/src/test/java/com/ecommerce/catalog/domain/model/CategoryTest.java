package com.ecommerce.catalog.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Category} aggregate root.
 *
 * <p>No Spring context — pure domain logic via the factory and domain methods.
 */
@Tag("unit")
class CategoryTest {

    // ── create() factory ───────────────────────────────────────────────────────

    @Test
    void create_rootCategory_hasNullParent() {
        Category category = Category.create("Electronics", null);

        assertThat(category.getParentId()).isNull();
        assertThat(category.isRoot()).isTrue();
    }

    @Test
    void create_childCategory_storesParentId() {
        CategoryId parentId = CategoryId.generate();

        Category category = Category.create("Laptops", parentId);

        assertThat(category.getParentId()).isEqualTo(parentId);
        assertThat(category.isRoot()).isFalse();
    }

    @Test
    void create_generatesNonNullId() {
        Category category = Category.create("Electronics", null);

        assertThat(category.getId()).isNotNull();
    }

    @Test
    void create_throwsIllegalArgumentException_whenNameIsBlank() {
        assertThatThrownBy(() -> Category.create("  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateDetails() ────────────────────────────────────────────────────────

    @Test
    void updateDetails_changesNameAndParent() {
        Category category = Category.create("Electronics", null);
        CategoryId newParent = CategoryId.generate();

        category.updateDetails("Consumer Electronics", newParent);

        assertThat(category.getName()).isEqualTo("Consumer Electronics");
        assertThat(category.getParentId()).isEqualTo(newParent);
    }

    @Test
    void updateDetails_toRootByPassingNullParent() {
        Category category = Category.create("Laptops", CategoryId.generate());

        category.updateDetails("Laptops", null);

        assertThat(category.isRoot()).isTrue();
    }

    @Test
    void updateDetails_throwsIllegalArgumentException_whenNameIsBlank() {
        Category category = Category.create("Electronics", null);

        assertThatThrownBy(() -> category.updateDetails("", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDetails_throwsIllegalArgumentException_whenParentIsSelf() {
        Category category = Category.create("Electronics", null);

        assertThatThrownBy(() -> category.updateDetails("Electronics", category.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── self-parent rejection at construction time ─────────────────────────────

    @Test
    void reconstitute_throwsIllegalArgumentException_whenParentIsSelf() {
        CategoryId id = CategoryId.generate();

        assertThatThrownBy(() -> Category.reconstitute(id, id, "Electronics"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── reconstitute() ─────────────────────────────────────────────────────────

    @Test
    void reconstitute_restoresExactState() {
        CategoryId id = CategoryId.generate();
        CategoryId parentId = CategoryId.generate();

        Category category = Category.reconstitute(id, parentId, "Laptops");

        assertThat(category.getId()).isEqualTo(id);
        assertThat(category.getParentId()).isEqualTo(parentId);
        assertThat(category.getName()).isEqualTo("Laptops");
    }
}

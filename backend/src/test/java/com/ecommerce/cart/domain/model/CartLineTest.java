package com.ecommerce.cart.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CartLine} value-style record — compact-constructor invariants only;
 * equals/hashCode are compiler-generated and not re-verified here (catalog house style skips
 * that too, see {@code ProductTest}).
 */
@Tag("unit")
class CartLineTest {

    private static final ProductId PRODUCT_ID = ProductId.generate();
    private static final ProductSnapshot SNAPSHOT = new ProductSnapshot("Widget", new Money(new BigDecimal("9.99"), "USD"));
    private static final Quantity QUANTITY = Quantity.of(1);
    private static final Instant ADDED_AT = Instant.parse("2026-07-31T12:00:00Z");

    @Test
    void constructor_throwsIllegalArgumentException_whenProductIdIsNull() {
        assertThatThrownBy(() -> new CartLine(null, SNAPSHOT, QUANTITY, false, ADDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenSnapshotIsNull() {
        assertThatThrownBy(() -> new CartLine(PRODUCT_ID, null, QUANTITY, false, ADDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenQuantityIsNull() {
        assertThatThrownBy(() -> new CartLine(PRODUCT_ID, SNAPSHOT, null, false, ADDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenAddedAtIsNull() {
        assertThatThrownBy(() -> new CartLine(PRODUCT_ID, SNAPSHOT, QUANTITY, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_storesAllFields_whenValid() {
        CartLine line = new CartLine(PRODUCT_ID, SNAPSHOT, QUANTITY, true, ADDED_AT);

        assertThat(line.productId()).isEqualTo(PRODUCT_ID);
        assertThat(line.snapshot()).isEqualTo(SNAPSHOT);
        assertThat(line.quantity()).isEqualTo(QUANTITY);
        assertThat(line.unavailable()).isTrue();
        assertThat(line.addedAt()).isEqualTo(ADDED_AT);
    }
}

package com.ecommerce.cart.domain.model;

import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link ProductSnapshot} value object — compact-constructor invariants.
 */
@Tag("unit")
class ProductSnapshotTest {

    private static final Money PRICE = new Money(new BigDecimal("19.99"), "USD");

    @Test
    void constructor_throwsIllegalArgumentException_whenNameIsNull() {
        assertThatThrownBy(() -> new ProductSnapshot(null, PRICE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenNameIsBlank() {
        assertThatThrownBy(() -> new ProductSnapshot("   ", PRICE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenUnitPriceIsNull() {
        assertThatThrownBy(() -> new ProductSnapshot("Widget", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_storesFields_whenValid() {
        ProductSnapshot snapshot = new ProductSnapshot("Widget", PRICE);

        assertThat(snapshot.name()).isEqualTo("Widget");
        assertThat(snapshot.unitPrice()).isEqualTo(PRICE);
    }
}

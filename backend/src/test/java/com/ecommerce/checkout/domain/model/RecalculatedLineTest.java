package com.ecommerce.checkout.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link RecalculatedLine} value object — compact-constructor invariants.
 */
@Tag("unit")
class RecalculatedLineTest {

    private static final ProductId PRODUCT_ID = ProductId.generate();
    private static final Quantity QUANTITY = Quantity.of(2);
    private static final Money PRICE = new Money(new BigDecimal("9.99"), "USD");

    @Test
    void constructor_throwsNullPointerException_whenProductIdIsNull() {
        assertThatThrownBy(() -> new RecalculatedLine(null, QUANTITY, PRICE, PRICE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenQuantityIsNull() {
        assertThatThrownBy(() -> new RecalculatedLine(PRODUCT_ID, null, PRICE, PRICE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenUnitPriceIsNull() {
        assertThatThrownBy(() -> new RecalculatedLine(PRODUCT_ID, QUANTITY, null, PRICE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenLineTotalIsNull() {
        assertThatThrownBy(() -> new RecalculatedLine(PRODUCT_ID, QUANTITY, PRICE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_storesFields_whenValid() {
        RecalculatedLine line = new RecalculatedLine(PRODUCT_ID, QUANTITY, PRICE, PRICE);

        assertThat(line.productId()).isEqualTo(PRODUCT_ID);
        assertThat(line.quantity()).isEqualTo(QUANTITY);
        assertThat(line.unitPrice()).isEqualTo(PRICE);
        assertThat(line.lineTotal()).isEqualTo(PRICE);
    }
}

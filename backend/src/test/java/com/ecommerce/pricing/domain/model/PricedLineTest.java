package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@link PricedLine} value-style record — compact-constructor invariants only. */
@Tag("unit")
class PricedLineTest {

    private static final ProductId PRODUCT_ID = ProductId.generate();
    private static final Quantity QUANTITY = Quantity.of(2);
    private static final Money BASE_PRICE = new Money(new BigDecimal("10.00"), "USD");
    private static final Money DISCOUNT = Money.zero("USD");
    private static final Money UNIT_FINAL = new Money(new BigDecimal("10.00"), "USD");
    private static final Money LINE_TOTAL = new Money(new BigDecimal("20.00"), "USD");

    @Test
    void constructor_throwsNullPointerException_whenProductIdIsNull() {
        assertThatThrownBy(() -> new PricedLine(null, QUANTITY, BASE_PRICE, DISCOUNT, UNIT_FINAL, LINE_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenQuantityIsNull() {
        assertThatThrownBy(() -> new PricedLine(PRODUCT_ID, null, BASE_PRICE, DISCOUNT, UNIT_FINAL, LINE_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenUnitBasePriceIsNull() {
        assertThatThrownBy(() -> new PricedLine(PRODUCT_ID, QUANTITY, null, DISCOUNT, UNIT_FINAL, LINE_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenUnitDiscountIsNull() {
        assertThatThrownBy(() -> new PricedLine(PRODUCT_ID, QUANTITY, BASE_PRICE, null, UNIT_FINAL, LINE_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenUnitFinalPriceIsNull() {
        assertThatThrownBy(() -> new PricedLine(PRODUCT_ID, QUANTITY, BASE_PRICE, DISCOUNT, null, LINE_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenLineTotalIsNull() {
        assertThatThrownBy(() -> new PricedLine(PRODUCT_ID, QUANTITY, BASE_PRICE, DISCOUNT, UNIT_FINAL, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_storesAllFields_whenValid() {
        PricedLine line = new PricedLine(PRODUCT_ID, QUANTITY, BASE_PRICE, DISCOUNT, UNIT_FINAL, LINE_TOTAL);

        assertThat(line.productId()).isEqualTo(PRODUCT_ID);
        assertThat(line.quantity()).isEqualTo(QUANTITY);
        assertThat(line.unitBasePrice()).isEqualTo(BASE_PRICE);
        assertThat(line.unitDiscount()).isEqualTo(DISCOUNT);
        assertThat(line.unitFinalPrice()).isEqualTo(UNIT_FINAL);
        assertThat(line.lineTotal()).isEqualTo(LINE_TOTAL);
    }
}

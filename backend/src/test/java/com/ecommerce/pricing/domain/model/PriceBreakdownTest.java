package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@link PriceBreakdown} value-style record — compact-constructor invariants only. */
@Tag("unit")
class PriceBreakdownTest {

    private static final Money AMOUNT = new Money(new BigDecimal("20.00"), "USD");
    private static final PricedLine LINE = new PricedLine(
            ProductId.generate(), Quantity.of(2), new Money(new BigDecimal("10.00"), "USD"),
            Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"), AMOUNT);
    private static final List<PricedLine> LINES = List.of(LINE);
    private static final EffectivePrice GRAND_TOTAL = new EffectivePrice(new Money(new BigDecimal("25.00"), "USD"));

    @Test
    void constructor_throwsNullPointerException_whenLinesIsNull() {
        assertThatThrownBy(() -> new PriceBreakdown(null, AMOUNT, Money.zero("USD"), AMOUNT, GRAND_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenLinesIsEmpty() {
        assertThatThrownBy(() -> new PriceBreakdown(List.of(), AMOUNT, Money.zero("USD"), AMOUNT, GRAND_TOTAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenSubtotalIsNull() {
        assertThatThrownBy(() -> new PriceBreakdown(LINES, null, Money.zero("USD"), AMOUNT, GRAND_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenDiscountTotalIsNull() {
        assertThatThrownBy(() -> new PriceBreakdown(LINES, AMOUNT, null, AMOUNT, GRAND_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenShippingFeeIsNull() {
        assertThatThrownBy(() -> new PriceBreakdown(LINES, AMOUNT, Money.zero("USD"), null, GRAND_TOTAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenGrandTotalIsNull() {
        assertThatThrownBy(() -> new PriceBreakdown(LINES, AMOUNT, Money.zero("USD"), AMOUNT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_returnsImmutableLinesCopy_whenValid() {
        PriceBreakdown breakdown = new PriceBreakdown(LINES, AMOUNT, Money.zero("USD"), AMOUNT, GRAND_TOTAL);

        assertThat(breakdown.lines()).containsExactly(LINE);
        assertThatThrownBy(() -> breakdown.lines().add(LINE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

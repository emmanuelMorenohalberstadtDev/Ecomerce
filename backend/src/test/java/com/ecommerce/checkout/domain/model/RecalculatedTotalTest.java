package com.ecommerce.checkout.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link RecalculatedTotal} value object — compact-constructor invariants and
 * the defensive {@code lines} copy, mirroring {@code cart.domain.model.ProductSnapshotTest}'s
 * style for a sibling value object.
 */
@Tag("unit")
class RecalculatedTotalTest {

    private static final Money MONEY = new Money(new BigDecimal("10.00"), "USD");
    private static final RecalculatedLine LINE = new RecalculatedLine(
            ProductId.generate(), Quantity.of(1), MONEY, MONEY);

    @Test
    void constructor_throwsNullPointerException_whenLinesIsNull() {
        assertThatThrownBy(() -> new RecalculatedTotal(null, MONEY, MONEY, MONEY, MONEY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenSubtotalIsNull() {
        assertThatThrownBy(() -> new RecalculatedTotal(List.of(), null, MONEY, MONEY, MONEY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenDiscountTotalIsNull() {
        assertThatThrownBy(() -> new RecalculatedTotal(List.of(), MONEY, null, MONEY, MONEY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenShippingFeeIsNull() {
        assertThatThrownBy(() -> new RecalculatedTotal(List.of(), MONEY, MONEY, null, MONEY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsNullPointerException_whenGrandTotalIsNull() {
        assertThatThrownBy(() -> new RecalculatedTotal(List.of(), MONEY, MONEY, MONEY, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_storesFields_whenValid() {
        RecalculatedTotal total = new RecalculatedTotal(List.of(LINE), MONEY, MONEY, MONEY, MONEY);

        assertThat(total.lines()).containsExactly(LINE);
        assertThat(total.subtotal()).isEqualTo(MONEY);
        assertThat(total.discountTotal()).isEqualTo(MONEY);
        assertThat(total.shippingFee()).isEqualTo(MONEY);
        assertThat(total.grandTotal()).isEqualTo(MONEY);
    }

    @Test
    void lines_returnsUnmodifiableList() {
        RecalculatedTotal total = new RecalculatedTotal(List.of(LINE), MONEY, MONEY, MONEY, MONEY);

        assertThatThrownBy(() -> total.lines().add(LINE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@link EffectivePrice} value-style record — compact-constructor invariants only. */
@Tag("unit")
class EffectivePriceTest {

    @Test
    void constructor_throwsNullPointerException_whenAmountIsNull() {
        assertThatThrownBy(() -> new EffectivePrice(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_storesAmount_whenValid() {
        Money amount = new Money(new BigDecimal("24.99"), "USD");

        EffectivePrice price = new EffectivePrice(amount);

        assertThat(price.amount()).isEqualTo(amount);
    }
}

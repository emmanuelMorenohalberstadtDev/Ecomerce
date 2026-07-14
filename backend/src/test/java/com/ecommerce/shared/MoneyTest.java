package com.ecommerce.shared;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency ARS = Currency.getInstance("ARS");

    @Test
    void shouldAddSameCurrency() {
        Money result = Money.of("100.50", "ARS").add(Money.of("49.50", "ARS"));

        assertThat(result).isEqualTo(Money.of("150.00", "ARS"));
    }

    @Test
    void shouldRejectCurrencyMismatch_onArithmetic() {
        assertThatThrownBy(() -> Money.of("10.00", "ARS").add(Money.of("10.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {"-0.01", "-100"})
    void shouldRejectNegativeAmounts(String amount) {
        assertThatThrownBy(() -> Money.of(amount, "ARS"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectScaleAboveFour() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.00001"), ARS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
    }

    @Test
    void shouldMultiplyByQuantity() {
        assertThat(Money.of("33.3333", "ARS").multiply(3))
                .isEqualTo(Money.of("99.9999", "ARS"));
    }

    @Test
    void shouldRoundHalfEven_onlyWhenApplyingRate() {
        // 100.0000 * 0.10125 = 10.125000 -> HALF_EVEN at scale 4 = 10.1250
        Money result = Money.of("100.00", "ARS").applyRate(new BigDecimal("0.10125"));

        assertThat(result).isEqualTo(Money.of("10.1250", "ARS"));
    }

    @Test
    void shouldCompareAmountsOfSameCurrency() {
        assertThat(Money.of("10.00", "ARS")).isGreaterThan(Money.of("9.99", "ARS"));
        assertThat(Money.zero(ARS).isZero()).isTrue();
    }
}

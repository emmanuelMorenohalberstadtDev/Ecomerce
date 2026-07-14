package com.ecommerce.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityTest {

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void shouldRejectValuesBelowOne(int value) {
        assertThatThrownBy(() -> new Quantity(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAddQuantities() {
        assertThat(new Quantity(2).add(new Quantity(3))).isEqualTo(new Quantity(5));
    }
}

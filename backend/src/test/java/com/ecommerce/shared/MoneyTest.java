package com.ecommerce.shared;

import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Money}.
 *
 * <p>No Spring context — pure JUnit 5.
 */
class MoneyTest {

    // ── add ──────────────────────────────────────────────────────────────────

    @Test
    void add_sumsTwoAmounts() {
        Money a = Money.of(new BigDecimal("10.00"), "USD");
        Money b = Money.of(new BigDecimal("5.50"), "USD");

        Money result = a.add(b);

        assertEquals(new BigDecimal("15.50"), result.amount());
        assertEquals("USD", result.currency());
    }

    @Test
    void add_withDifferentCurrency_throwsIllegalArgumentException() {
        Money usd = Money.of(new BigDecimal("10.00"), "USD");
        Money eur = Money.of(new BigDecimal("5.00"), "EUR");

        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
    }

    // ── subtract ─────────────────────────────────────────────────────────────

    @Test
    void subtract_producesCorrectResult() {
        Money a = Money.of(new BigDecimal("20.00"), "USD");
        Money b = Money.of(new BigDecimal("7.50"), "USD");

        Money result = a.subtract(b);

        assertEquals(new BigDecimal("12.50"), result.amount());
    }

    @Test
    void subtract_resultNegative_throwsIllegalArgumentException() {
        Money a = Money.of(new BigDecimal("5.00"), "USD");
        Money b = Money.of(new BigDecimal("10.00"), "USD");

        assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
    }

    // ── multiply ─────────────────────────────────────────────────────────────

    @Test
    void multiply_appliesHalfUpRounding() {
        // 0.015 * 3 = 0.045 → rounds to 0.05 with HALF_UP at scale 2
        // But compact constructor normalises input to scale 2 first: 0.015 → 0.02
        // Then 0.02 * 3 = 0.06
        // Use a clean value to test multiplication rounding independently:
        // 3.333... = 10.00 / 3 isn't representable; instead:
        // amount = 1.005 (which rounds to 1.01 at scale 2 in compact constructor)
        // multiply by 3 = 3.03
        Money price = Money.of(new BigDecimal("1.005"), "USD"); // normalised to 1.01
        Money result = price.multiply(3);

        assertEquals(new BigDecimal("3.03"), result.amount());
    }

    @Test
    void multiply_byZero_returnsZeroAmount() {
        Money price = Money.of(new BigDecimal("9.99"), "USD");
        Money result = price.multiply(0);

        assertTrue(result.isZero());
    }

    // ── zero factory ─────────────────────────────────────────────────────────

    @Test
    void zero_returnsAmountOfZero() {
        Money z = Money.zero("USD");

        assertTrue(z.isZero());
        assertEquals("USD", z.currency());
    }

    // ── of — validation ───────────────────────────────────────────────────────

    @Test
    void of_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("-0.01"), "USD"));
    }

    @Test
    void of_nullAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(null, "USD"));
    }

    @Test
    void of_nullCurrency_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.ONE, null));
    }

    @Test
    void of_blankCurrency_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.ONE, "  "));
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_isReadable() {
        Money m = Money.of(new BigDecimal("19.99"), "USD");
        assertEquals("19.99 USD", m.toString());
    }

    // ── isGreaterThan / isLessThan ────────────────────────────────────────────

    @Test
    void isGreaterThan_returnsTrueWhenHigher() {
        Money a = Money.of(new BigDecimal("20.00"), "USD");
        Money b = Money.of(new BigDecimal("10.00"), "USD");

        assertTrue(a.isGreaterThan(b));
        assertFalse(b.isGreaterThan(a));
    }

    @Test
    void isLessThan_returnsTrueWhenLower() {
        Money a = Money.of(new BigDecimal("5.00"), "USD");
        Money b = Money.of(new BigDecimal("10.00"), "USD");

        assertTrue(a.isLessThan(b));
        assertFalse(b.isLessThan(a));
    }
}

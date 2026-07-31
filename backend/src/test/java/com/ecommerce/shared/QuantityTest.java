package com.ecommerce.shared;

import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Quantity}.
 *
 * <p>No Spring context — pure JUnit 5.
 */
class QuantityTest {

    // ── of — validation ───────────────────────────────────────────────────────

    @Test
    void of_zero_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Quantity.of(0));
    }

    @Test
    void of_negative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Quantity.of(-1));
    }

    @Test
    void of_positiveValue_createsQuantity() {
        Quantity q = Quantity.of(5);
        assertEquals(5, q.value());
    }

    // ── add ──────────────────────────────────────────────────────────────────

    @Test
    void add_sumsTwoQuantities() {
        Quantity a = Quantity.of(3);
        Quantity b = Quantity.of(4);

        assertEquals(7, a.add(b).value());
    }

    // ── subtract ─────────────────────────────────────────────────────────────

    @Test
    void subtract_resultPositive_succeeds() {
        Quantity a = Quantity.of(10);
        Quantity b = Quantity.of(3);

        assertEquals(7, a.subtract(b).value());
    }

    @Test
    void subtract_resultZero_throwsIllegalArgumentException() {
        Quantity a = Quantity.of(5);
        Quantity b = Quantity.of(5);

        assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
    }

    @Test
    void subtract_resultNegative_throwsIllegalArgumentException() {
        Quantity a = Quantity.of(2);
        Quantity b = Quantity.of(5);

        assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
    }

    // ── canFulfill ────────────────────────────────────────────────────────────

    @Test
    void canFulfill_requestedLessThanOrEqualToValue_returnsTrue() {
        Quantity stock = Quantity.of(10);

        assertTrue(stock.canFulfill(10));
        assertTrue(stock.canFulfill(5));
        assertTrue(stock.canFulfill(1));
    }

    @Test
    void canFulfill_requestedMoreThanValue_returnsFalse() {
        Quantity stock = Quantity.of(3);

        assertFalse(stock.canFulfill(4));
        assertFalse(stock.canFulfill(100));
    }

    // ── isGreaterThan / isAtLeast ─────────────────────────────────────────────

    @Test
    void isGreaterThan_returnsTrueWhenHigher() {
        assertTrue(Quantity.of(5).isGreaterThan(Quantity.of(3)));
        assertFalse(Quantity.of(3).isGreaterThan(Quantity.of(5)));
        assertFalse(Quantity.of(3).isGreaterThan(Quantity.of(3)));
    }

    @Test
    void isAtLeast_returnsTrueWhenValueMeetsThreshold() {
        assertTrue(Quantity.of(5).isAtLeast(5));
        assertTrue(Quantity.of(5).isAtLeast(3));
        assertFalse(Quantity.of(3).isAtLeast(5));
    }
}

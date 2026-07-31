package com.ecommerce.shared.quantity;

/**
 * Immutable value object representing a strictly positive unit count.
 *
 * <p>Invariant: {@code value} is always {@code >= 1}. A {@code Quantity} of zero is
 * meaningless in this domain — use absence of items rather than zero quantity.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record Quantity(int value) {

    // ── Compact constructor ───────────────────────────────────────────────────

    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive (> 0), got: " + value);
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    public Quantity add(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    /**
     * Subtracts {@code other} from this quantity.
     *
     * @throws IllegalArgumentException if the result would be zero or negative,
     *         because a {@code Quantity} must remain positive after any operation.
     */
    public Quantity subtract(Quantity other) {
        int result = this.value - other.value;
        if (result <= 0) {
            throw new IllegalArgumentException(
                    "Quantity subtraction would result in a non-positive value: "
                            + this.value + " - " + other.value + " = " + result);
        }
        return new Quantity(result);
    }

    // ── Comparison helpers ────────────────────────────────────────────────────

    public boolean isGreaterThan(Quantity other) {
        return this.value > other.value;
    }

    public boolean isAtLeast(int n) {
        return this.value >= n;
    }

    /**
     * Returns {@code true} if this stock quantity is sufficient to fulfil the
     * requested number of units.
     */
    public boolean canFulfill(int requested) {
        return this.value >= requested;
    }

    // ── Presentation ──────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}

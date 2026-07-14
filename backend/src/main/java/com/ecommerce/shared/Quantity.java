package com.ecommerce.shared;

/**
 * Shared-kernel quantity value object: always at least 1
 * (per-line maximums are business configuration, enforced by the owning aggregate).
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 1) {
            throw new IllegalArgumentException("quantity must be at least 1: " + value);
        }
    }

    public Quantity add(Quantity other) {
        return new Quantity(value + other.value);
    }
}

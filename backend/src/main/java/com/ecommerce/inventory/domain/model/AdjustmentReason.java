package com.ecommerce.inventory.domain.model;

/**
 * Immutable value object carrying the free-form justification an admin supplies when manually
 * correcting a {@link StockItem}'s available quantity (domain-model.md §2 inventory
 * {@code AdjustmentReason}; maps to {@code stock_movements.reason}).
 *
 * <p>Bounded to 500 characters to match the {@code stock_movements.reason} column's practical
 * text-length discipline used across this schema for free-form admin-supplied text (no explicit
 * CHECK on this column in V004, but api-guidelines §8.2 requires size bounds on every String at
 * the boundary; this is the domain-side backstop).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record AdjustmentReason(String value) {

    public static final int MAX_LENGTH = 500;

    public AdjustmentReason {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AdjustmentReason must not be null or blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "AdjustmentReason must be at most " + MAX_LENGTH + " characters, got: " + value.length());
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

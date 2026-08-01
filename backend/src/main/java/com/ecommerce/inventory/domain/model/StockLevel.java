package com.ecommerce.inventory.domain.model;

/**
 * Immutable value object representing the quantity of a product currently available to reserve
 * (domain-model.md §2, {@code StockItem.available}).
 *
 * <p>Invariant: {@code value} is always {@code >= 0}, mirroring
 * {@code ck_stock_items_quantity_available} in {@code V004__create_inventory_schema.sql}. Unlike
 * {@link com.ecommerce.shared.quantity.Quantity} (always strictly positive — a line can't hold
 * zero units), a stock level legitimately reaches zero (sold out).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record StockLevel(int value) {

    public StockLevel {
        if (value < 0) {
            throw new IllegalArgumentException("StockLevel must be >= 0, got: " + value);
        }
    }

    public static StockLevel of(int value) {
        return new StockLevel(value);
    }

    public static StockLevel zero() {
        return new StockLevel(0);
    }

    public boolean canFulfill(int requested) {
        return this.value >= requested;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}

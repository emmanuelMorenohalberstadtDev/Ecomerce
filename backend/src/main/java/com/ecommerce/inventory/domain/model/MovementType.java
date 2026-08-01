package com.ecommerce.inventory.domain.model;

/**
 * Kind of stock change recorded by one {@link StockMovement} ledger row
 * (database-design.md §3.4/§3.7; mirrors {@code ck_stock_movements_type} in
 * {@code V004__create_inventory_schema.sql}).
 *
 * <ul>
 *   <li>{@code RESERVE} — checkout hold decrements {@code quantity_available}; negative
 *       {@code quantityDelta}.</li>
 *   <li>{@code COMMIT} — payment success; the decrement already happened at {@code RESERVE} time,
 *       so this carries {@code quantityDelta = 0} and exists purely for traceability.</li>
 *   <li>{@code RELEASE} — checkout failure/expiry/order cancellation restocks; positive
 *       {@code quantityDelta}.</li>
 *   <li>{@code ADMIN_ADJUSTMENT} — manual correction; {@code quantityDelta} may be positive or
 *       negative.</li>
 * </ul>
 */
public enum MovementType {
    RESERVE,
    COMMIT,
    RELEASE,
    ADMIN_ADJUSTMENT
}

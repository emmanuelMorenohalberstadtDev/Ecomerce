package com.ecommerce.inventory.domain.model;

/**
 * Lifecycle status of a {@link StockReservation} (domain-model.md §2).
 *
 * <p>One-way transitions only: {@code HELD -> COMMITTED} (payment succeeds) or
 * {@code HELD -> RELEASED} (checkout failure/expiry, order cancellation). Never
 * {@code COMMITTED}/{@code RELEASED} back to {@code HELD} — enforced in
 * {@link StockReservation#commit()}/{@link StockReservation#release()}, not here; this enum only
 * names the legal values, mirroring {@code ck_stock_reservations_status} in
 * {@code V004__create_inventory_schema.sql}.
 */
public enum ReservationStatus {
    HELD,
    COMMITTED,
    RELEASED
}

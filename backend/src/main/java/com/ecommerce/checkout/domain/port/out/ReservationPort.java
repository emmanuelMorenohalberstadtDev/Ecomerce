package com.ecommerce.checkout.domain.port.out;

import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Checkout's own outbound port representing what it needs from the inventory context —
 * deliberately checkout-shaped (ADR-0003 §Decision item 2, mirroring
 * {@code cart.domain.port.out.ProductCatalogPort}'s relationship to catalog's
 * {@code ProductLookupPort}).
 *
 * <p>Implemented by {@code checkout.infrastructure.adapter.ReservationAdapter}, whose only
 * dependency on inventory is the inventory-owned
 * {@code com.ecommerce.inventory.application.port.StockReservationPort} bean — the sanctioned
 * crossing point.
 */
public interface ReservationPort {

    /**
     * @throws com.ecommerce.checkout.domain.exception.InsufficientStockForCheckoutException if any
     *         line's stock cannot be fully reserved — translated from inventory's façade-owned
     *         {@code StockReservationPort.InsufficientStockException}
     */
    ReservationOutcome reserve(CheckoutSessionId checkoutSessionId, List<ReservationLine> lines, Instant expiresAt);

    /**
     * @throws InvalidCheckoutSessionStateException if the reservation is not currently held —
     *         translated from inventory's façade-owned
     *         {@code StockReservationPort.InvalidReservationStateException}
     */
    void release(ReservationId reservationId);

    /** One requested (product, quantity) pair. */
    record ReservationLine(ProductId productId, Quantity quantity) {

        public ReservationLine {
            Objects.requireNonNull(productId, "ReservationLine productId must not be null");
            Objects.requireNonNull(quantity, "ReservationLine quantity must not be null");
        }
    }

    /** The reservation created. */
    record ReservationOutcome(ReservationId reservationId) {

        public ReservationOutcome {
            Objects.requireNonNull(reservationId, "ReservationOutcome reservationId must not be null");
        }
    }
}

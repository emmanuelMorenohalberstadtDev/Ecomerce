package com.ecommerce.order.infrastructure.adapter;

import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.ReservationId;

import java.util.Objects;

/**
 * Implements order's {@link ReservationPort} by delegating to inventory's public
 * {@link StockReservationPort} — the only dependency this adapter (and, transitively, the order
 * context) has on the inventory context (ADR-0004 §Decision item 3).
 *
 * <p>Translates inventory's façade-owned nested exceptions into order's own domain exceptions —
 * order's application/domain layers never see an inventory type.
 */
public class ReservationAdapter implements ReservationPort {

    private final StockReservationPort stockReservationPort;

    public ReservationAdapter(StockReservationPort stockReservationPort) {
        this.stockReservationPort = Objects.requireNonNull(stockReservationPort);
    }

    @Override
    public void commit(ReservationId reservationId) {
        try {
            stockReservationPort.commit(reservationId);
        } catch (StockReservationPort.InvalidReservationStateException e) {
            throw new InvalidOrderTransitionException(e.getMessage());
        }
    }

    @Override
    public void release(ReservationId reservationId) {
        try {
            stockReservationPort.release(reservationId);
        } catch (StockReservationPort.InvalidReservationStateException e) {
            throw new InvalidOrderTransitionException(e.getMessage());
        }
    }
}

package com.ecommerce.checkout.infrastructure.adapter;

import com.ecommerce.checkout.domain.exception.InsufficientStockForCheckoutException;
import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Implements checkout's {@link ReservationPort} by delegating to inventory's public
 * {@link StockReservationPort} — the only dependency this adapter (and, transitively, the
 * checkout context) has on the inventory context (ADR-0003 §Decision item 2).
 *
 * <p>Translates inventory's façade-owned nested exceptions into checkout's own domain exceptions
 * — checkout's application/domain layers never see an inventory type. Both ports declare a
 * nested {@code ReservationLine}/{@code ReservationOutcome} record of their own (deliberately
 * independent shapes, ADR-0003 §Decision item 2) — this class always fully qualifies the
 * inventory-side ones to keep the two unambiguous.
 */
public class ReservationAdapter implements ReservationPort {

    private final StockReservationPort stockReservationPort;

    public ReservationAdapter(StockReservationPort stockReservationPort) {
        this.stockReservationPort = Objects.requireNonNull(stockReservationPort);
    }

    @Override
    public ReservationOutcome reserve(CheckoutSessionId checkoutSessionId, List<ReservationLine> lines,
                                      Instant expiresAt) {
        List<StockReservationPort.ReservationLine> requestLines = lines.stream()
                .map(l -> new StockReservationPort.ReservationLine(l.productId(), l.quantity()))
                .toList();

        StockReservationPort.ReservationOutcome outcome;
        try {
            outcome = stockReservationPort.reserve(
                    new StockReservationPort.ReservationRequest(checkoutSessionId, requestLines, expiresAt));
        } catch (StockReservationPort.InsufficientStockException e) {
            throw new InsufficientStockForCheckoutException(e.getMessage());
        }

        return new ReservationOutcome(outcome.reservationId());
    }

    @Override
    public void release(ReservationId reservationId) {
        try {
            stockReservationPort.release(reservationId);
        } catch (StockReservationPort.InvalidReservationStateException e) {
            throw new InvalidCheckoutSessionStateException(e.getMessage());
        }
    }
}

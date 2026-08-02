package com.ecommerce.order.domain.port.out;

import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.shared.id.ReservationId;

/**
 * Order's own outbound port representing what it needs from the inventory context — deliberately
 * order-shaped (ADR-0004 §Decision item 3), mirroring
 * {@code checkout.domain.port.out.ReservationPort}'s relationship to inventory's
 * {@code StockReservationPort}. Unlike checkout's mirror, order never {@code reserve}s — it only
 * drives the two terminal transitions of a reservation checkout already created.
 *
 * <p>Implemented by {@code order.infrastructure.adapter.ReservationAdapter}, whose only dependency
 * on inventory is the inventory-owned
 * {@code com.ecommerce.inventory.application.port.StockReservationPort} bean — the sanctioned
 * crossing point.
 *
 * <p><strong>Order drives both transitions synchronously through this port, in its own use case's
 * transaction — never through a listener</strong> (ADR-0004 §Decision item 3): one owner per
 * reservation-state transition, avoiding the race two independent releasers/committers could
 * otherwise hit.
 */
public interface ReservationPort {

    /**
     * Commits a {@code HELD} reservation to {@code COMMITTED} on payment success
     * ({@code AdminMarkOrderPaidUseCase}).
     *
     * @throws InvalidOrderTransitionException if the reservation is not currently held —
     *         translated from inventory's façade-owned
     *         {@code StockReservationPort.InvalidReservationStateException}
     */
    void commit(ReservationId reservationId);

    /**
     * Releases a {@code HELD} reservation ({@code CancelOrderUseCase}, {@code AdminCancelOrderUseCase}).
     * <strong>Never called for the {@code PLACED -> FAILED} path</strong> — checkout already
     * released synchronously before publishing {@code CheckoutSessionExpiredEvent} (ADR-0004
     * sanity check); see {@code FailOrderFromCheckoutExpiryUseCase}'s javadoc.
     *
     * @throws InvalidOrderTransitionException if the reservation is not currently held —
     *         translated from inventory's façade-owned
     *         {@code StockReservationPort.InvalidReservationStateException}
     */
    void release(ReservationId reservationId);
}

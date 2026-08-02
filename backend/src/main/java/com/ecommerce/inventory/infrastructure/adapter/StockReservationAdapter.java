package com.ecommerce.inventory.infrastructure.adapter;

import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.inventory.application.usecase.ReleaseReservationUseCase;
import com.ecommerce.inventory.application.usecase.ReservationLineInput;
import com.ecommerce.inventory.application.usecase.ReserveStockUseCase;
import com.ecommerce.inventory.application.usecase.ReserveStockUseCase.ReserveStockCommand;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.shared.id.ReservationId;

import java.util.List;
import java.util.Objects;

/**
 * Implements inventory's {@link StockReservationPort} by delegating to
 * {@link ReserveStockUseCase}/{@link ReleaseReservationUseCase} — the only dependency this
 * adapter has on inventory's application internals. Mirrors
 * {@code cart.infrastructure.adapter.ProductCatalogAdapter}.
 *
 * <p>Translates inventory's own domain exceptions (private to inventory's domain package) into
 * this façade's nested exception types — a cross-context caller (checkout) must never catch or
 * import an inventory {@code domain.exception} type (ADR-0003 §Decision item 2).
 */
public class StockReservationAdapter implements StockReservationPort {

    private final ReserveStockUseCase reserveStockUseCase;
    private final ReleaseReservationUseCase releaseReservationUseCase;

    public StockReservationAdapter(ReserveStockUseCase reserveStockUseCase,
                                   ReleaseReservationUseCase releaseReservationUseCase) {
        this.reserveStockUseCase = Objects.requireNonNull(reserveStockUseCase);
        this.releaseReservationUseCase = Objects.requireNonNull(releaseReservationUseCase);
    }

    @Override
    public ReservationOutcome reserve(ReservationRequest request) {
        List<ReservationLineInput> lines = request.lines().stream()
                .map(l -> new ReservationLineInput(l.productId(), l.quantity()))
                .toList();

        StockReservation reservation;
        try {
            reservation = reserveStockUseCase.execute(
                    new ReserveStockCommand(request.checkoutSessionId(), lines, request.expiresAt()));
        } catch (com.ecommerce.inventory.domain.exception.InsufficientStockException e) {
            // Fully qualified deliberately: StockReservationPort declares a nested type with the
            // identical simple name (InsufficientStockException), which shadows a single-type
            // import of the domain exception for unqualified name resolution inside this class
            // body (JLS member-type inheritance). An unqualified catch here would silently bind
            // to the nested façade type instead of the domain exception ReserveStockUseCase
            // actually throws, and this catch clause would never fire. Do not re-add the import.
            throw new StockReservationPort.InsufficientStockException(e.getMessage());
        }

        List<ReservationLine> outcomeLines = reservation.getLines().stream()
                .map(l -> new ReservationLine(l.productId(), l.quantity()))
                .toList();
        return new ReservationOutcome(reservation.getId(), outcomeLines);
    }

    @Override
    public void release(ReservationId reservationId) {
        try {
            releaseReservationUseCase.execute(reservationId);
        } catch (com.ecommerce.inventory.domain.exception.InvalidReservationStateException e) {
            // Fully qualified deliberately: see the shadowing note in reserve() above. Same
            // hazard applies to InvalidReservationStateException.
            throw new StockReservationPort.InvalidReservationStateException(e.getMessage());
        }
    }
}

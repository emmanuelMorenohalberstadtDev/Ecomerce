package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.exception.InvalidReservationStateException;
import com.ecommerce.inventory.domain.exception.ReservationNotFoundException;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.ReservationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Commits a {@code HELD} reservation to {@code COMMITTED} on payment success (domain-model.md §1
 * inventory, rule 6). Not exposed over REST — an in-process port for the future checkout context.
 *
 * <p><strong>Does not touch {@code quantity_available}</strong> — the decrement already happened
 * at reserve time (database-design.md §3.4). Writes one {@code stock_movements} row per line with
 * {@code movement_type = COMMIT} and {@code quantityDelta = 0} purely for traceability (per-line,
 * matching the {@code RESERVE} rows written by {@link ReserveStockUseCase} one-for-one, rather
 * than a single reservation-level row — makes the ledger queryable per product either way).
 *
 * <p><strong>No domain event published.</strong> domain-model.md §4's event catalog lists
 * {@code StockReservedEvent}/{@code StockReleasedEvent} for inventory but no
 * {@code StockCommittedEvent} — checked deliberately, not an oversight.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class CommitReservationUseCase {

    private final StockReservationRepository stockReservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    public CommitReservationUseCase(StockReservationRepository stockReservationRepository,
                                    StockMovementRepository stockMovementRepository,
                                    Clock clock) {
        this.stockReservationRepository = Objects.requireNonNull(stockReservationRepository);
        this.stockMovementRepository = Objects.requireNonNull(stockMovementRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public StockReservation execute(ReservationId reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + reservationId));

        reservation.commit(); // throws InvalidReservationStateException if not HELD (in-memory check)
        // Atomic, DB-level backstop against a concurrent commit/release race on the same
        // reservation (e.g. an admin manual commit racing the checkout-expiry sweep's release) —
        // the in-memory check above only guards against the snapshot read at findById() time.
        boolean updated = stockReservationRepository.updateStatus(reservation.getId(), reservation.getStatus());
        if (!updated) {
            throw new InvalidReservationStateException(
                    "Reservation " + reservation.getId() + " was concurrently modified and is no longer HELD");
        }

        Instant now = clock.instant();
        for (ReservedLine line : reservation.getLines()) {
            stockMovementRepository.record(new StockMovement(
                    line.productId(), MovementType.COMMIT, 0, reservation.getId(), null, null, now));
        }

        return reservation;
    }
}

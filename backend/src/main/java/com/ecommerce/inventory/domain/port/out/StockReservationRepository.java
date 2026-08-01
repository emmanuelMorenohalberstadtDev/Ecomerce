package com.ecommerce.inventory.domain.port.out;

import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain port (outbound) for {@link StockReservation} persistence.
 *
 * <p><strong>{@link #create} is insert-only, called exactly once per reservation:</strong>
 * {@code stock_reservation_lines} grants are {@code SELECT, INSERT} only (V004 §5) — this port
 * deliberately has no general {@code save(StockReservation)} that would re-persist the whole
 * aggregate (lines included) on every status change, which risks Hibernate attempting an
 * {@code UPDATE}/{@code DELETE} against a table the DB role cannot write that way to.
 * {@link #updateStatus} is the only mutation after creation — a header-only column update that
 * never touches the lines collection.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaStockReservationRepository} in
 * infrastructure. One repository per aggregate root (domain-model.md §2 ownership rules).
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and
 * {@code repository_interfaces_live_in_domain}.
 */
public interface StockReservationRepository {

    /** Inserts a brand-new {@code HELD} reservation together with its lines. Called once per reservation. */
    StockReservation create(StockReservation reservation);

    /** Loads a reservation together with its lines. */
    Optional<StockReservation> findById(ReservationId id);

    /**
     * Updates only the {@code status} column of the reservation identified by {@code id} — never
     * touches {@code stock_reservation_lines}. Used by commit/release after the in-memory
     * {@link StockReservation#commit()}/{@link StockReservation#release()} transition has already
     * validated the state change.
     */
    void updateStatus(ReservationId id, ReservationStatus newStatus);

    /**
     * Finds every {@code HELD} reservation whose {@code expires_at} is before {@code now} —
     * serves the payment-window expiry sweep (rules 5, 8) via
     * {@code idx_reservations_expiry_held}. Returned reservations include their lines (the sweep
     * needs them to restock on release).
     */
    List<StockReservation> findHeldExpiredAsOf(Instant now);
}

package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.event.StockReleasedEvent;
import com.ecommerce.inventory.domain.exception.ReservationNotFoundException;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.ReservationId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Releases a {@code HELD} reservation to {@code RELEASED} and restocks every line (domain-model.md
 * §1 inventory, rule 7 — checkout failure/expiry, order cancellation). Not exposed over REST — an
 * in-process port for the future checkout/order contexts, and the step {@link ExpireReservationsUseCase}
 * delegates to per expired reservation.
 *
 * <p>For each line: atomically increments {@code stock_items.quantity_available}
 * ({@link StockItemRepository#incrementAvailable} — unconditional restock, rule 7) and writes a
 * {@code stock_movements} row ({@code RELEASE}, positive {@code quantityDelta}). Publishes
 * {@link StockReleasedEvent} once, after every line is restocked and logged.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class ReleaseReservationUseCase {

    private final StockReservationRepository stockReservationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReleaseReservationUseCase(StockReservationRepository stockReservationRepository,
                                     StockItemRepository stockItemRepository,
                                     StockMovementRepository stockMovementRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.stockReservationRepository = Objects.requireNonNull(stockReservationRepository);
        this.stockItemRepository = Objects.requireNonNull(stockItemRepository);
        this.stockMovementRepository = Objects.requireNonNull(stockMovementRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public StockReservation execute(ReservationId reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + reservationId));

        reservation.release(); // throws InvalidReservationStateException if not HELD
        stockReservationRepository.updateStatus(reservation.getId(), reservation.getStatus());

        Instant now = clock.instant();
        for (ReservedLine line : reservation.getLines()) {
            stockItemRepository.incrementAvailable(line.productId(), line.quantity().value());
            stockMovementRepository.record(new StockMovement(
                    line.productId(), MovementType.RELEASE, line.quantity().value(),
                    reservation.getId(), null, null, now));
        }

        eventPublisher.publishEvent(new StockReleasedEvent(
                reservation.getId(), reservation.getCheckoutSessionId(), reservation.getLines(), now));

        return reservation;
    }
}

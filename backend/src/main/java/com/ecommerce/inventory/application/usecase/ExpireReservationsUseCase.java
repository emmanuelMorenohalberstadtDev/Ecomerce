package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.exception.InvalidReservationStateException;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sweeps every {@code HELD} reservation whose payment window has passed and releases it
 * (rules 5, 8). A plain callable use case, not a {@code @Scheduled} job — the brief for this
 * change is to make the sweep invocable; wiring an actual scheduler/trigger (cron, checkout-owned
 * timer, etc.) is left to whichever context ends up owning "when to sweep" (devops-engineer /
 * checkout, once it exists).
 *
 * <p><strong>Deliberately not wrapped in one outer {@code @Transactional}.</strong> Each
 * {@link ReleaseReservationUseCase#execute} call is its own proxied bean invocation and therefore
 * its own transaction (default {@code REQUIRED} propagation opens a new one since none is active
 * here) — a failure releasing reservation N does not roll back the releases already committed for
 * reservations 1..N-1, and a sweep touching many reservations does not hold every one of their
 * row locks in a single long-lived transaction.
 *
 * <p><strong>Tolerates a specific, expected race:</strong> the query behind
 * {@link StockReservationRepository#findHeldExpiredAsOf} and the per-reservation
 * {@code release()} call are not atomic with each other — between the two, a concurrent checkout
 * could have already committed or released the same reservation (e.g. payment succeeded in the
 * same instant the sweep picked it up). That lands as {@link InvalidReservationStateException}
 * from {@link StockReservation#release()}; this loop catches exactly that one narrow, anticipated
 * exception type, logs it, and continues to the next reservation — it is not a broad
 * catch-and-swallow of unexpected failures (constraint: "no catching Exception broadly"), it is
 * the documented outcome of a known benign race.
 */
@Service
public class ExpireReservationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireReservationsUseCase.class);

    private final StockReservationRepository stockReservationRepository;
    private final ReleaseReservationUseCase releaseReservationUseCase;
    private final Clock clock;

    public ExpireReservationsUseCase(StockReservationRepository stockReservationRepository,
                                     ReleaseReservationUseCase releaseReservationUseCase,
                                     Clock clock) {
        this.stockReservationRepository = Objects.requireNonNull(stockReservationRepository);
        this.releaseReservationUseCase = Objects.requireNonNull(releaseReservationUseCase);
        this.clock = Objects.requireNonNull(clock);
    }

    /** @return the reservations this sweep actually released (excludes any skipped by the race described above) */
    public List<StockReservation> execute() {
        List<StockReservation> expired = stockReservationRepository.findHeldExpiredAsOf(clock.instant());
        List<StockReservation> released = new ArrayList<>();
        for (StockReservation reservation : expired) {
            try {
                released.add(releaseReservationUseCase.execute(reservation.getId()));
            } catch (InvalidReservationStateException e) {
                log.warn("Skipped expiry release for reservation {} — no longer HELD "
                        + "(concurrent commit/release already happened): {}",
                        reservation.getId(), e.getMessage());
            }
        }
        return released;
    }
}

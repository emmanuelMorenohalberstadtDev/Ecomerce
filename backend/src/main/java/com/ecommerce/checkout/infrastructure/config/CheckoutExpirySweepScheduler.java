package com.ecommerce.checkout.infrastructure.config;

import com.ecommerce.checkout.application.usecase.ExpirePaymentWindowUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Periodically triggers {@link ExpirePaymentWindowUseCase}'s payment-window sweep.
 *
 * <p><strong>No {@code @Scheduled} precedent exists anywhere else in this codebase</strong> —
 * verified by search before adding this class. {@code inventory.application.usecase.ExpireReservationsUseCase}
 * is deliberately left unscheduled (ADR-0003 §Decision item 3), and no other job in the project
 * runs on a timer today. This class is therefore the first, and is kept intentionally minimal: a
 * single {@code @Component} carrying both {@link EnableScheduling} (scoped to this one class
 * rather than the main application class, so enabling the scheduling infrastructure is visible
 * exactly where the one scheduled job lives — {@code @EnableScheduling} works the same way on a
 * component-scanned "lite" configuration class as on a full {@code @Configuration} class) and the
 * trigger method itself — no separate job-scheduling framework (e.g. Quartz) is justified for a
 * single, simple, idempotent-to-repeat sweep.
 *
 * <p><strong>Fixed delay of 60 seconds</strong> (not fixed rate): {@code fixedDelay} starts timing
 * the next run only after the previous run completes, which cannot pile up overlapping sweeps if
 * one run is ever unusually slow — a safer default than {@code fixedRate} for a job with unbounded
 * per-run work (however many sessions are due). 60 seconds is a reasonable balance for a
 * 15-minute payment window: a session may sit expired-but-unswept for at most one interval, far
 * smaller than the window itself, without hammering the database every few seconds. Not derived
 * from any documented requirement — an operational default, adjustable without any code change if
 * a future need arises (would become a {@code CheckoutProperties} field at that point).
 *
 * <p>Runs the sweep result through a debug-level log only — the per-session outcome (including
 * the one benign, expected race the use case already handles) is logged inside
 * {@link ExpirePaymentWindowUseCase} itself.
 */
@Component
@EnableScheduling
public class CheckoutExpirySweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckoutExpirySweepScheduler.class);

    private final ExpirePaymentWindowUseCase expirePaymentWindowUseCase;

    public CheckoutExpirySweepScheduler(ExpirePaymentWindowUseCase expirePaymentWindowUseCase) {
        this.expirePaymentWindowUseCase = Objects.requireNonNull(expirePaymentWindowUseCase);
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        List<?> expired = expirePaymentWindowUseCase.execute();
        if (!expired.isEmpty()) {
            log.debug("Checkout payment-window sweep expired {} session(s)", expired.size());
        }
    }
}

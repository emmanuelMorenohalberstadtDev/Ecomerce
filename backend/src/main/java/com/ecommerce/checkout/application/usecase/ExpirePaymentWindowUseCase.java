package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sweeps every {@code AWAITING_PAYMENT} checkout session whose payment window has passed and
 * releases it (ADR-0003 §Decision item 3: checkout owns its own payment-window sweep;
 * {@code inventory.application.usecase.ExpireReservationsUseCase}'s independent sweep is
 * deliberately left unscheduled/uninvoked, to avoid two sweepers racing to release the same
 * reservation — see ADR-0003 Consequences).
 *
 * <p><strong>Deliberately not wrapped in one outer {@code @Transactional}.</strong> Each
 * {@link ExpireCheckoutSessionUseCase#execute} call is its own proxied bean invocation and
 * therefore its own transaction (default {@code REQUIRED} propagation opens a new one since none
 * is active here) — mirrors
 * {@code inventory.application.usecase.ExpireReservationsUseCase}'s reasoning exactly: a failure
 * expiring session N does not roll back the expiries already committed for sessions 1..N-1, and a
 * sweep touching many sessions does not hold every one of their row locks in a single
 * long-lived transaction.
 *
 * <p><strong>Tolerates a specific, expected race:</strong> the query behind
 * {@link CheckoutSessionRepository#findAwaitingPaymentExpiredAsOf} and the per-session
 * release-and-expire are not atomic with each other — a concurrent flow could already have moved
 * the session/reservation on. That surfaces as {@link InvalidCheckoutSessionStateException} (from
 * either {@code CheckoutSession#expire()}'s own guard or the reservation port's translated
 * inventory-side guard); this loop catches exactly that one narrow, anticipated exception type,
 * logs it, and continues to the next session — not a broad catch-and-swallow of unexpected
 * failures.
 */
@Service
public class ExpirePaymentWindowUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirePaymentWindowUseCase.class);

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ExpireCheckoutSessionUseCase expireCheckoutSessionUseCase;
    private final Clock clock;

    public ExpirePaymentWindowUseCase(CheckoutSessionRepository checkoutSessionRepository,
                                      ExpireCheckoutSessionUseCase expireCheckoutSessionUseCase,
                                      Clock clock) {
        this.checkoutSessionRepository = Objects.requireNonNull(checkoutSessionRepository);
        this.expireCheckoutSessionUseCase = Objects.requireNonNull(expireCheckoutSessionUseCase);
        this.clock = Objects.requireNonNull(clock);
    }

    /** @return the sessions this sweep actually expired (excludes any skipped by the race described above) */
    public List<CheckoutSession> execute() {
        List<CheckoutSession> candidates = checkoutSessionRepository.findAwaitingPaymentExpiredAsOf(clock.instant());
        List<CheckoutSession> expired = new ArrayList<>();
        for (CheckoutSession session : candidates) {
            try {
                expired.add(expireCheckoutSessionUseCase.execute(session));
            } catch (InvalidCheckoutSessionStateException e) {
                log.warn("Skipped expiry for checkout session {} — no longer AWAITING_PAYMENT or its "
                                + "reservation already moved on (concurrent commit/release already happened): {}",
                        session.getId(), e.getMessage());
            }
        }
        return expired;
    }
}

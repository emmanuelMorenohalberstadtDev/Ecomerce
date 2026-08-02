package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutSessionExpiredEvent;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Releases one {@code AWAITING_PAYMENT} checkout session's reservation, transitions it to
 * {@code EXPIRED}, persists, and publishes {@link CheckoutSessionExpiredEvent} — all in one
 * transaction.
 *
 * <p><strong>Extracted as its own {@code @Transactional} bean</strong> (rather than a private
 * method on {@link ExpirePaymentWindowUseCase}) purely so the sweep can invoke it as a proxied
 * bean call per session — a private/self-invoked method would silently skip Spring's
 * transactional proxy (skill {@code transactions}: "pitfalls like self-invocation"). This is
 * exactly why {@code inventory.application.usecase.ExpireReservationsUseCase} delegates to the
 * standalone {@code ReleaseReservationUseCase} bean rather than inlining the release logic in its
 * own sweep loop — this class plays the identical role for checkout.
 *
 * <p>Not exposed over REST — an internal collaborator used only by
 * {@link ExpirePaymentWindowUseCase}'s sweep.
 */
@Service
@Transactional
public class ExpireCheckoutSessionUseCase {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReservationPort reservationPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ExpireCheckoutSessionUseCase(CheckoutSessionRepository checkoutSessionRepository,
                                        ReservationPort reservationPort,
                                        ApplicationEventPublisher eventPublisher,
                                        Clock clock) {
        this.checkoutSessionRepository = Objects.requireNonNull(checkoutSessionRepository);
        this.reservationPort = Objects.requireNonNull(reservationPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public CheckoutSession execute(CheckoutSession session) {
        reservationPort.release(session.getReservationId());

        session.expire(); // throws InvalidCheckoutSessionStateException if not AWAITING_PAYMENT
        CheckoutSession saved = checkoutSessionRepository.save(session);

        Instant now = clock.instant();
        eventPublisher.publishEvent(new CheckoutSessionExpiredEvent(
                saved.getId(), saved.getCustomerId(), saved.getReservationId(), now));

        return saved;
    }
}

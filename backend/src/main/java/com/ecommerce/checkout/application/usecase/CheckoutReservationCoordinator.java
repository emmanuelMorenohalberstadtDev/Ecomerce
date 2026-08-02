package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.port.out.CartLinesPort.CartLineView;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import com.ecommerce.checkout.domain.port.out.ReservationPort.ReservationLine;
import com.ecommerce.checkout.domain.port.out.ReservationPort.ReservationOutcome;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Package-private helper shared by {@link StartCheckoutUseCase} and
 * {@link ConfirmRecalculatedTotalUseCase} — centralises "reserve stock for this session's cart
 * lines, transition the session to AWAITING_PAYMENT, persist, and publish the order hand-off
 * event" so the two use cases do not duplicate this block. Not a public API, not a god utility —
 * one job, package-scoped, mirroring {@code cart.application.usecase.CartLookup}.
 *
 * <p>Plain constructor-injected collaborator, not a Spring bean — each owning use case
 * constructs its own instance in its constructor. This class declares no
 * {@code @Transactional} of its own: its work always runs inside the calling use case's
 * already-open transaction.
 */
final class CheckoutReservationCoordinator {

    private final ReservationPort reservationPort;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration paymentWindow;

    CheckoutReservationCoordinator(ReservationPort reservationPort,
                                   CheckoutSessionRepository checkoutSessionRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock,
                                   Duration paymentWindow) {
        this.reservationPort = Objects.requireNonNull(reservationPort);
        this.checkoutSessionRepository = Objects.requireNonNull(checkoutSessionRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.paymentWindow = Objects.requireNonNull(paymentWindow);
    }

    /**
     * Reserves stock for {@code lines}, transitions {@code session} to AWAITING_PAYMENT, persists
     * it, and publishes {@link CheckoutAwaitingPaymentEvent} — all within the caller's own open
     * transaction.
     */
    CheckoutSession reserveAndMoveToAwaitingPayment(CheckoutSession session, List<CartLineView> lines) {
        Instant now = clock.instant();
        Instant deadline = now.plus(paymentWindow);

        List<ReservationLine> reservationLines = lines.stream()
                .map(l -> new ReservationLine(l.productId(), l.quantity()))
                .toList();
        ReservationOutcome outcome = reservationPort.reserve(session.getId(), reservationLines, deadline);

        // Captured BEFORE the repository round-trip deliberately: JpaCheckoutSessionRepository's
        // toDomain() always reconstructs RecalculatedTotal with an EMPTY lines list (no per-line
        // child table exists for checkout_sessions — see that class's javadoc), so `saved` below
        // would yield an empty list here. `session`'s in-memory RecalculatedTotal (computed moments
        // earlier by the price quote step, never round-tripped through persistence) is the only
        // place the non-empty per-line breakdown still exists at this point (ADR-0004 item 1).
        List<CheckoutAwaitingPaymentEvent.Line> eventLines = session.getRecalculatedTotal().lines().stream()
                .map(l -> new CheckoutAwaitingPaymentEvent.Line(l.productId(), l.quantity(), l.unitPrice(), l.lineTotal()))
                .toList();

        session.moveToAwaitingPayment(outcome.reservationId(), deadline);
        CheckoutSession saved = checkoutSessionRepository.save(session);

        eventPublisher.publishEvent(new CheckoutAwaitingPaymentEvent(
                saved.getId(), saved.getCustomerId(), saved.getCartId(), saved.getReservationId(), eventLines,
                session.getRecalculatedTotal().discountTotal(), session.getRecalculatedTotal().shippingFee(),
                saved.getRecalculatedTotal().grandTotal(), saved.getPaymentDeadline(), now));

        return saved;
    }
}

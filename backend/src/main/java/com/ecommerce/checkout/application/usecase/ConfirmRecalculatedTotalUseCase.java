package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.exception.CartNotCheckoutableException;
import com.ecommerce.checkout.domain.exception.CheckoutSessionNotFoundException;
import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.model.SessionStatus;
import com.ecommerce.checkout.domain.port.out.CartLinesPort;
import com.ecommerce.checkout.domain.port.out.CartLinesPort.CartLineView;
import com.ecommerce.checkout.domain.port.out.CartLinesPort.CartSnapshot;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.checkout.domain.port.out.PriceQuotePort;
import com.ecommerce.checkout.domain.port.out.PriceQuotePort.PriceQuoteLine;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.money.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Confirms a customer's acceptance of the recalculated total shown by a prior
 * {@link StartCheckoutUseCase} (or a previous call to this same use case) and proceeds to reserve
 * stock (domain-model.md §1 checkout; ADR-0003 in-scope use case).
 *
 * <p><strong>Re-verifies rather than trusting the stale total (rule 2, server-side price
 * authority):</strong> prices can move again between the prior recalculation and this call, so
 * this use case re-runs {@link PriceQuotePort#recalculate} from the session's current cart lines
 * rather than trusting the {@link RecalculatedTotal} already stored on the session.
 * <strong>Deliberate simplicity choice, documented:</strong> if the freshly recalculated total
 * again disagrees with what the customer is now confirming, this use case does NOT loop or
 * re-prompt recursively — it simply refreshes the session's stored {@link RecalculatedTotal} to
 * the newest value, leaves the session in {@code PENDING_CONFIRMATION}, and returns
 * {@link CheckoutOutcome.NeedsReconfirmation} again, the same shape {@link StartCheckoutUseCase}
 * returns on the first mismatch. The client is expected to re-display the (now newest) total and
 * call this endpoint again — an infinite-loop guard at the server (a retry counter, a cap) would
 * add state-machine complexity this v1 does not need; a total that keeps moving on every call is
 * an edge case rare enough that "ask again" is an acceptable UX, not a defect.
 *
 * <p>Ownership-scoped: loads the session via
 * {@link CheckoutSessionRepository#findByIdAndCustomerId} (security-architecture §3.2/§3.3) —
 * {@link CheckoutSessionNotFoundException} (404) for a nonexistent id or one owned by someone
 * else, identically. {@link InvalidCheckoutSessionStateException} (422) if the session is not
 * currently {@code PENDING_CONFIRMATION}.
 *
 * <p>Shares the reserve-and-transition step with {@link StartCheckoutUseCase} via the
 * package-private {@link CheckoutReservationCoordinator} rather than duplicating it.
 *
 * <p>Transaction boundary is this use case class.
 *
 * <p><strong>Not {@code @Service}-annotated</strong> — wired exclusively via an explicit
 * {@code @Bean} method in {@code CheckoutInfrastructureConfiguration}, for the same reason as
 * {@link StartCheckoutUseCase} (see that class's javadoc): the payment-window {@link Duration}
 * must be resolved from config in {@code infrastructure.config} and handed in as a plain value.
 */
@Transactional
public class ConfirmRecalculatedTotalUseCase {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CartLinesPort cartLinesPort;
    private final PriceQuotePort priceQuotePort;
    private final CheckoutReservationCoordinator reservationCoordinator;

    public ConfirmRecalculatedTotalUseCase(CheckoutSessionRepository checkoutSessionRepository,
                                           CartLinesPort cartLinesPort,
                                           PriceQuotePort priceQuotePort,
                                           ReservationPort reservationPort,
                                           ApplicationEventPublisher eventPublisher,
                                           Clock clock,
                                           Duration paymentWindow) {
        this.checkoutSessionRepository = Objects.requireNonNull(checkoutSessionRepository);
        this.cartLinesPort = Objects.requireNonNull(cartLinesPort);
        this.priceQuotePort = Objects.requireNonNull(priceQuotePort);
        this.reservationCoordinator = new CheckoutReservationCoordinator(
                reservationPort, this.checkoutSessionRepository, eventPublisher, clock, paymentWindow);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public CheckoutOutcome execute(ConfirmRecalculatedTotalCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        CheckoutSession session = checkoutSessionRepository
                .findByIdAndCustomerId(command.checkoutSessionId(), command.customerId())
                .orElseThrow(() -> new CheckoutSessionNotFoundException(
                        "No checkout session " + command.checkoutSessionId() + " for the current customer"));

        if (session.getStatus() != SessionStatus.PENDING_CONFIRMATION) {
            throw new InvalidCheckoutSessionStateException(
                    "CheckoutSession " + session.getId() + " is not PENDING_CONFIRMATION (status="
                            + session.getStatus() + ")");
        }

        CartSnapshot cart = cartLinesPort.findActiveCartLines(command.customerId())
                .orElseThrow(() -> new CartNotCheckoutableException(
                        "Customer " + command.customerId() + " no longer has an active cart to check out"));

        RecalculatedTotal freshTotal = priceQuotePort.recalculate(toPriceLines(cart.lines()));
        session.refreshRecalculatedTotal(freshTotal);

        if (!freshTotal.grandTotal().equals(command.confirmedTotal())) {
            CheckoutSession saved = checkoutSessionRepository.save(session);
            return new CheckoutOutcome.NeedsReconfirmation(saved);
        }

        CheckoutSession confirmed = reservationCoordinator.reserveAndMoveToAwaitingPayment(session, cart.lines());
        return new CheckoutOutcome.Confirmed(confirmed);
    }

    private static List<PriceQuoteLine> toPriceLines(List<CartLineView> lines) {
        return lines.stream().map(l -> new PriceQuoteLine(l.productId(), l.quantity())).toList();
    }

    /**
     * @param checkoutSessionId the session being confirmed
     * @param customerId        the caller, resolved from the JWT principal — ownership check
     * @param confirmedTotal    the total the customer is now re-confirming (the one they were
     *                          just shown) — carried only for comparison, never as arithmetic input
     */
    public record ConfirmRecalculatedTotalCommand(CheckoutSessionId checkoutSessionId, CustomerId customerId,
                                                  Money confirmedTotal) {

        public ConfirmRecalculatedTotalCommand {
            Objects.requireNonNull(checkoutSessionId, "checkoutSessionId must not be null");
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(confirmedTotal, "confirmedTotal must not be null");
        }
    }
}

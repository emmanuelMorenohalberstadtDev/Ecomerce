package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.exception.CartNotCheckoutableException;
import com.ecommerce.checkout.domain.exception.CheckoutSessionAlreadyOpenException;
import com.ecommerce.checkout.domain.exception.DuplicateIdempotencyKeyException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Starts a checkout session for the caller's active cart (domain-model.md §1 checkout; ADR-0003
 * in-scope use case).
 *
 * <p><strong>Open-session guard (security review gate, HIGH — reservation-spam / stock-lock DoS):
 * </strong> {@link #execute} first checks
 * {@link CheckoutSessionRepository#findOpenByCustomerId} and rejects the call with
 * {@link CheckoutSessionAlreadyOpenException} (409) if the customer already has a session in
 * {@code PENDING_CONFIRMATION} or {@code AWAITING_PAYMENT}. This check runs <em>before</em> the
 * idempotency-key check below: it is the more fundamental guard (one open session per customer,
 * regardless of key), and failing fast on it avoids an unnecessary idempotency-key lookup for the
 * common abuse shape (same cart, fresh key every call). Without this guard, {@code
 * CartLinesPort#findActiveCartLines} being a read-only lookup — starting checkout never locks or
 * deactivates the source cart — would let a single authenticated CUSTOMER call
 * {@code POST /checkout-sessions} repeatedly against the same active cart with a fresh
 * {@code Idempotency-Key} each time, and each call would independently reserve real stock via
 * {@link ReservationPort} (through {@link CheckoutReservationCoordinator}) for the full payment
 * window — an inventory-griefing DoS against exactly the asset security-architecture.md §1.1 calls
 * out ("Stock &amp; coupon caps ... Oversell, cap-drain fraud"). <strong>Known, accepted
 * limitation:</strong> a customer whose only open session has genuinely expired is excluded from
 * {@code findOpenByCustomerId} only once {@link ExpirePaymentWindowUseCase}'s sweep has
 * transitioned it to {@code EXPIRED} — there is a small window, bounded by the sweep's fixed-delay
 * interval, where such a customer is briefly blocked from starting a new checkout. This is not
 * solved here by having this use case lazily expire stale sessions itself: that would blur this
 * use case's transaction boundary and duplicate {@link ExpirePaymentWindowUseCase}'s
 * responsibility. Acceptable for v1.
 *
 * <p><strong>Idempotency (api-guidelines §5, security-architecture §1.4 case 6):</strong> the
 * {@code Idempotency-Key} is checked <em>before</em> any reservation is attempted (ADR-0003
 * §C.1) — {@link #execute} looks up {@link CheckoutSessionRepository#findByCustomerIdAndIdempotencyKey}
 * next; if a session already used this key, this call throws {@link DuplicateIdempotencyKeyException}
 * (409) rather than attempting to re-run the flow. <strong>Design choice, documented:</strong>
 * this is the simple "any replay of an already-used key is a 409" contract, not the richer
 * "detect an exact replay and return the original result" variant api-guidelines §5.3 also
 * sanctions — implementing exact-replay detection would require comparing the full request body
 * against what produced the stored session, unnecessary complexity for v1's single consumer of
 * this contract. A genuine concurrent double-submit of the same key races on
 * {@code uq_checkout_sessions_idempotency} at the database level (now scoped per
 * {@code (customer_id, idempotency_key)}, api-guidelines §5.5 — a global key uniqueness constraint
 * would let an accidental key collision between two unrelated customers spuriously 409 one of
 * them); the unique-constraint violation is the backstop for the check-then-act gap between the
 * lookup above and the insert in {@link CheckoutSessionRepository#create} (translated to the same
 * 409 by the persistence adapter).
 *
 * <p><strong>Price mismatch — result, not exception:</strong> when the recalculated total differs
 * from {@code expectedTotal}, this is an expected alternate outcome (server-side price authority,
 * security-architecture §1.4 threat 2), not a rule violation — per backend-architecture §5
 * ("expected alternate outcomes are results, not exceptions"). {@link #execute} returns a sealed
 * {@link CheckoutOutcome}: {@link CheckoutOutcome.NeedsReconfirmation} carries the session
 * persisted as {@code PENDING_CONFIRMATION} with no reservation made yet;
 * {@link CheckoutOutcome.Confirmed} carries the session persisted as {@code AWAITING_PAYMENT}
 * with stock reserved and the payment window running. The controller maps
 * {@code NeedsReconfirmation} to 200 (api-guidelines §2: "mutation succeeded, useful body") and
 * {@code Confirmed} to 201 (a reservation — a new resource state — was created). A dedicated
 * {@code price-changed} problem type (already registered, api-guidelines §3.2) is therefore not
 * exercised by this endpoint — the mismatch is surfaced as a normal 2xx result, not a thrown
 * exception.
 *
 * <p>Transaction boundary is this use case class — reserving stock still calls out to inventory's
 * own transaction via {@link ReservationPort} (backend-architecture §3: "each port call executes
 * in the target's own transaction"); this method's own transaction covers the session
 * create/save and event publication.
 *
 * <p><strong>Not {@code @Service}-annotated:</strong> unlike most use cases in this codebase,
 * this class is wired exclusively via an explicit {@code @Bean} method in
 * {@code CheckoutInfrastructureConfiguration} — mirrors
 * {@code pricing.application.usecase.CalculateEffectivePriceUseCase}'s reasoning exactly: the
 * payment-window {@link Duration} is a policy value resolved from
 * {@code CheckoutProperties}/{@code application.yml}, which must happen in
 * {@code infrastructure.config} (application must never read {@code @ConfigurationProperties}
 * directly — ArchitectureTest rule 4) and be handed into this constructor as a plain value.
 * Annotating this class {@code @Service} in addition to that explicit {@code @Bean} method would
 * register it twice.
 */
@Transactional
public class StartCheckoutUseCase {

    private final CartLinesPort cartLinesPort;
    private final PriceQuotePort priceQuotePort;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final Clock clock;
    private final CheckoutReservationCoordinator reservationCoordinator;

    public StartCheckoutUseCase(CartLinesPort cartLinesPort,
                                PriceQuotePort priceQuotePort,
                                ReservationPort reservationPort,
                                CheckoutSessionRepository checkoutSessionRepository,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock,
                                Duration paymentWindow) {
        this.cartLinesPort = Objects.requireNonNull(cartLinesPort);
        this.priceQuotePort = Objects.requireNonNull(priceQuotePort);
        this.checkoutSessionRepository = Objects.requireNonNull(checkoutSessionRepository);
        this.clock = Objects.requireNonNull(clock);
        this.reservationCoordinator = new CheckoutReservationCoordinator(
                reservationPort, this.checkoutSessionRepository, eventPublisher, this.clock, paymentWindow);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public CheckoutOutcome execute(StartCheckoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<CheckoutSession> existingOpenSession =
                checkoutSessionRepository.findOpenByCustomerId(command.customerId());
        if (existingOpenSession.isPresent()) {
            throw new CheckoutSessionAlreadyOpenException(
                    "Customer " + command.customerId() + " already has an open checkout session: "
                            + existingOpenSession.get().getId());
        }

        Optional<CheckoutSession> existingByKey = checkoutSessionRepository
                .findByCustomerIdAndIdempotencyKey(command.customerId(), command.idempotencyKey());
        if (existingByKey.isPresent()) {
            throw new DuplicateIdempotencyKeyException(
                    "Idempotency-Key has already been used to start a checkout session: "
                            + command.idempotencyKey());
        }

        CartSnapshot cart = cartLinesPort.findActiveCartLines(command.customerId())
                .orElseThrow(() -> new CartNotCheckoutableException(
                        "Customer " + command.customerId() + " has no active cart to check out"));
        if (cart.lines().isEmpty()) {
            throw new CartNotCheckoutableException("Cart " + cart.cartId() + " has no lines to check out");
        }

        RecalculatedTotal recalculatedTotal = priceQuotePort.recalculate(toPriceLines(cart.lines()));

        Instant now = clock.instant();
        CheckoutSession session = CheckoutSession.start(CheckoutSessionId.generate(), command.customerId(),
                cart.cartId(), recalculatedTotal, command.expectedTotal(), command.idempotencyKey(), now);

        boolean reconfirmationNeeded = session.requiresReconfirmation();
        CheckoutSession created = checkoutSessionRepository.create(session);

        if (reconfirmationNeeded) {
            return new CheckoutOutcome.NeedsReconfirmation(created);
        }

        CheckoutSession confirmed = reservationCoordinator.reserveAndMoveToAwaitingPayment(created, cart.lines());
        return new CheckoutOutcome.Confirmed(confirmed);
    }

    private static List<PriceQuoteLine> toPriceLines(List<CartLineView> lines) {
        return lines.stream().map(l -> new PriceQuoteLine(l.productId(), l.quantity())).toList();
    }

    /**
     * @param customerId     the caller, resolved from the JWT principal — never from the request
     *                       body (api-guidelines §7.4)
     * @param idempotencyKey the mandatory {@code Idempotency-Key} header value (api-guidelines §5.1)
     * @param expectedTotal  the total the client/cart believed BEFORE recalculation — carried only
     *                       for re-confirmation comparison, never as arithmetic input
     *                       (api-guidelines §8.5)
     */
    public record StartCheckoutCommand(CustomerId customerId, String idempotencyKey, Money expectedTotal) {

        public StartCheckoutCommand {
            Objects.requireNonNull(customerId, "customerId must not be null");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey must not be null or blank");
            }
            Objects.requireNonNull(expectedTotal, "expectedTotal must not be null");
        }
    }
}

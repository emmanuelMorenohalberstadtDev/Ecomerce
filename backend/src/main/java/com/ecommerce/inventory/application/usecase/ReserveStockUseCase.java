package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.domain.event.StockReservedEvent;
import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ReservationId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Reserves stock for a batch of (product, quantity) lines at checkout time (domain-model.md §1
 * inventory, rule 6). Not exposed over REST in this change — an in-process port for the future
 * checkout context to call directly (checkout has no migration/module yet; see the inventory
 * handoff report).
 *
 * <p><strong>Semantics: all-or-nothing per reservation.</strong> Each line's atomic conditional
 * decrement ({@link StockItemRepository#decrementIfSufficientStock}) runs in turn; the moment one
 * line comes back short, {@link InsufficientStockException} is thrown and the whole method aborts
 * — Spring rolls back this {@code @Transactional} method's transaction, which undoes every
 * decrement already applied earlier in the loop (they were never committed). No compensation code
 * is needed for that: the transaction boundary itself provides all-or-nothing for free. This was
 * chosen over a partial-success/shortage-report contract because inventory-facing task guidance
 * calls all-or-nothing "the safer default", and it matches how a checkout batch is actually
 * consumed (a checkout with 3 lines where only 2 can be filled is not a valid order to place;
 * proceeding with a partial hold would require the caller to immediately release the successful
 * lines anyway).
 *
 * <p>Writes one {@code stock_movements} row per line ({@code RESERVE}, negative
 * {@code quantityDelta}) and publishes {@link StockReservedEvent} after the reservation and its
 * ledger rows are persisted — all inside this one transaction (published-after-commit in spirit;
 * this codebase's established pattern, per catalog's {@code ChangeProductPriceUseCase}, publishes
 * synchronously within the use case's own transaction rather than gating on an
 * {@code @TransactionalEventListener}, since there are no subscribers yet to desynchronize from).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class ReserveStockUseCase {

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReserveStockUseCase(StockItemRepository stockItemRepository,
                               StockReservationRepository stockReservationRepository,
                               StockMovementRepository stockMovementRepository,
                               ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.stockItemRepository = Objects.requireNonNull(stockItemRepository);
        this.stockReservationRepository = Objects.requireNonNull(stockReservationRepository);
        this.stockMovementRepository = Objects.requireNonNull(stockMovementRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public StockReservation execute(ReserveStockCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("ReserveStockCommand must contain at least one line");
        }

        for (ReservationLineInput line : command.lines()) {
            boolean decremented = stockItemRepository.decrementIfSufficientStock(
                    line.productId(), line.quantity().value());
            if (!decremented) {
                // Aborts the method; the @Transactional rollback undoes every decrement already
                // applied earlier in this loop (all-or-nothing — see class javadoc).
                throw new InsufficientStockException(
                        "Insufficient stock for product " + line.productId()
                                + ": requested " + line.quantity().value());
            }
        }

        Instant now = clock.instant();
        List<ReservedLine> reservedLines = command.lines().stream()
                .map(l -> new ReservedLine(l.productId(), l.quantity()))
                .toList();
        Expiry expiry = command.expiresAt() == null ? Expiry.none() : Expiry.at(command.expiresAt());
        StockReservation reservation = StockReservation.create(
                ReservationId.generate(), command.checkoutSessionId(), reservedLines, expiry, now);
        StockReservation saved = stockReservationRepository.create(reservation);

        for (ReservedLine line : saved.getLines()) {
            stockMovementRepository.record(new StockMovement(
                    line.productId(), MovementType.RESERVE, -line.quantity().value(),
                    saved.getId(), null, null, now));
        }

        eventPublisher.publishEvent(
                new StockReservedEvent(saved.getId(), saved.getCheckoutSessionId(), saved.getLines(), now));

        return saved;
    }

    /**
     * @param checkoutSessionId the checkout session this hold is created for
     * @param lines              the batch of (product, quantity) requests; must be non-empty
     * @param expiresAt          nullable payment-window deadline — computed and owned by the
     *                           checkout context (domain-model.md §1), inventory only stores it
     */
    public record ReserveStockCommand(CheckoutSessionId checkoutSessionId,
                                      List<ReservationLineInput> lines, Instant expiresAt) {}
}

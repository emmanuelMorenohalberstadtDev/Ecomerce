package com.ecommerce.order.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderPlacedEvent;
import com.ecommerce.order.domain.exception.DuplicateOrderException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.order.domain.port.out.ProductCatalogPort;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Creates an {@link Order} in {@code PLACED} from checkout's hand-off
 * ({@code CheckoutAwaitingPaymentEvent}, ADR-0004) — invoked by
 * {@code PlaceOrderFromCheckoutListener}'s {@code @TransactionalEventListener(AFTER_COMMIT)}, not
 * directly over REST.
 *
 * <p><strong>Idempotency (ADR-0004):</strong> checks {@link OrderRepository#findByReservationId}
 * up front; if an order already exists for this reservation, logs and no-ops rather than creating
 * a duplicate. {@code uq_orders_reservation} ({@code V006__create_order_schema.sql}) is the
 * DB-level backstop for the race between this check and the insert —
 * {@link OrderRepository#create} translates a lost race into
 * {@code DuplicateOrderException}, which this method catches and treats identically to the
 * up-front hit (log, no-op).
 *
 * <p><strong>Never re-prices</strong> (rule 3): every {@link LineSnapshot}'s price/quantity comes
 * straight from {@code event.lines()} — the exact {@code RecalculatedTotal} checkout already
 * confirmed and reserved against. Only the product's display name is resolved fresh, via
 * {@link ProductCatalogPort}, falling back to a placeholder
 * ({@code "Unavailable product " + productId}) rather than failing placement if the product was
 * retired in the narrow window between checkout's commit and this listener firing (ADR-0004
 * §Decision item 1).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class PlaceOrderFromCheckoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderFromCheckoutUseCase.class);

    private final OrderRepository orderRepository;
    private final ProductCatalogPort productCatalogPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlaceOrderFromCheckoutUseCase(OrderRepository orderRepository,
                                         ProductCatalogPort productCatalogPort,
                                         ApplicationEventPublisher eventPublisher,
                                         Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.productCatalogPort = Objects.requireNonNull(productCatalogPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void execute(CheckoutAwaitingPaymentEvent event) {
        if (orderRepository.findByReservationId(event.reservationId()).isPresent()) {
            log.info("Order already exists for reservation {} — skipping duplicate placement "
                    + "(checkoutSessionId={})", event.reservationId(), event.checkoutSessionId());
            return;
        }

        List<LineSnapshot> lines = event.lines().stream()
                .map(this::toLineSnapshot)
                .toList();

        Money itemsTotal = lines.stream()
                .map(LineSnapshot::lineTotal)
                .reduce(Money.zero(event.grandTotal().currency()), Money::add);
        OrderTotals totals = new OrderTotals(itemsTotal, event.discountTotal(), event.shippingTotal(), event.grandTotal());

        Instant now = clock.instant();
        Order order = Order.place(OrderId.generate(), event.customerId(), lines, totals,
                null /* coupon: not carried on this event in v1 — see handoff report */,
                event.reservationId(), Actor.system(), now);

        Order saved;
        try {
            saved = orderRepository.create(order);
        } catch (DuplicateOrderException e) {
            log.info("Lost the race to place an order for reservation {} — another AFTER_COMMIT "
                    + "firing already created it: {}", event.reservationId(), e.getMessage());
            return;
        }

        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getId(), saved.getCustomerId(),
                saved.getReservationId(), now));
    }

    private LineSnapshot toLineSnapshot(CheckoutAwaitingPaymentEvent.Line line) {
        String name = productCatalogPort.findActiveProduct(line.productId())
                .map(ProductCatalogPort.CatalogProductView::name)
                .orElseGet(() -> "Unavailable product " + line.productId());
        return new LineSnapshot(line.productId(), name, line.unitPrice(), line.quantity());
    }
}

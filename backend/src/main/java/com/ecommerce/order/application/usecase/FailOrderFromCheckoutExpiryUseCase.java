package com.ecommerce.order.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutSessionExpiredEvent;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderFailedEvent;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Transitions an {@link Order} {@code PLACED -> FAILED} when its payment window lapsed
 * ({@code checkout.domain.event.CheckoutSessionExpiredEvent}, rules 5/8, ADR-0004) — invoked by
 * {@code FailOrderFromCheckoutExpiryListener}'s {@code @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p><strong>"No order yet" is handled gracefully, not thrown</strong>: whether an
 * {@code Order} was already placed from the <em>matching</em> {@code CheckoutAwaitingPaymentEvent}
 * by the time this fires depends on both listeners' relative timing — a session can expire before
 * its order was ever placed (e.g. {@code PlaceOrderFromCheckoutListener} hasn't run yet, or failed
 * per its own javadoc's accepted-and-logged consistency story). This method finds the order by
 * {@link OrderRepository#findByReservationId} and simply returns if none exists yet — not an
 * error, a legitimate race.
 *
 * <p><strong>Must NOT call {@code ReservationPort.release}</strong> — checkout's
 * {@code ExpireCheckoutSessionUseCase} already released the reservation synchronously, before
 * publishing {@code CheckoutSessionExpiredEvent} (ADR-0003 §Decision item 3). Calling release again
 * here would hit inventory's already-released-state guard (ADR-0004 sanity check) — this class
 * therefore has no {@code ReservationPort} dependency at all.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class FailOrderFromCheckoutExpiryUseCase {

    private static final Logger log = LoggerFactory.getLogger(FailOrderFromCheckoutExpiryUseCase.class);

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FailOrderFromCheckoutExpiryUseCase(OrderRepository orderRepository,
                                              ApplicationEventPublisher eventPublisher,
                                              Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void execute(CheckoutSessionExpiredEvent event) {
        Optional<Order> maybeOrder = orderRepository.findByReservationId(event.reservationId());
        if (maybeOrder.isEmpty()) {
            log.info("No order exists yet for reservation {} — nothing to fail (checkoutSessionId={})",
                    event.reservationId(), event.checkoutSessionId());
            return;
        }

        Order order = maybeOrder.get();
        Instant now = clock.instant();
        try {
            order.fail(Actor.system(), now); // throws InvalidOrderTransitionException if not PLACED
        } catch (InvalidOrderTransitionException e) {
            // The order already moved on (e.g. paid) between the reservation expiring and this
            // listener firing — a legitimate, narrow race, not an error to propagate.
            log.warn("Skipped failing order {} for reservation {} — no longer PLACED: {}",
                    order.getId(), event.reservationId(), e.getMessage());
            return;
        }
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderFailedEvent(saved.getId(), saved.getCustomerId(),
                saved.getReservationId(), now));
    }
}

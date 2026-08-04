package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderPaidEvent;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Transitions an {@link Order} {@code PLACED -> PAID} on a successful payment capture — backs
 * {@code order.application.port.OrderPaymentPort#markPaid} (ADR-0005 §Decision item 1).
 *
 * <p><strong>Same body as {@code AdminMarkOrderPaidUseCase}</strong> (commit the reservation
 * synchronously via {@link ReservationPort#commit} in this same transaction, save, publish
 * {@link OrderPaidEvent}) with three deliberate differences, mirroring
 * {@code FailOrderFromCheckoutExpiryUseCase}'s already-established shape for system-actor
 * transitions:
 * <ul>
 *   <li>{@link Actor#system()} instead of {@code Actor.admin(actorId)} — a customer paying for
 *       their own order is not an admin action.</li>
 *   <li>No {@code @PreAuthorize} — this use case has no direct HTTP entry point; its only caller
 *       is {@code order.infrastructure.adapter.OrderPaymentAdapter}, reached through payment's
 *       already-{@code @PreAuthorize("hasRole('CUSTOMER')")}-gated {@code SubmitPaymentUseCase}.</li>
 *   <li>No {@code AuditLogPort} write — security §6c's admin audit log is for <em>admin</em>
 *       actions specifically.</li>
 * </ul>
 *
 * <p><strong>Duplication accepted</strong> per the same precedent ADR-0004 already established for
 * {@code FailOrderFromCheckoutExpiryUseCase} vs the admin cancel path (ADR-0005 §Consequences).
 *
 * <p>Transaction boundary is this use case class — but since its only caller,
 * {@code SubmitPaymentUseCase}, is itself {@code @Transactional}, default Spring {@code REQUIRED}
 * propagation joins that already-open transaction rather than starting a new one. This is the
 * mechanism ADR-0005 relies on to keep the gateway call, the {@code Payment} mutation, this
 * transition, and the reservation commit inside one physical DB transaction.
 */
@Service
@Transactional
public class MarkOrderPaidFromPaymentUseCase {

    private final OrderRepository orderRepository;
    private final ReservationPort reservationPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MarkOrderPaidFromPaymentUseCase(OrderRepository orderRepository,
                                           ReservationPort reservationPort,
                                           ApplicationEventPublisher eventPublisher,
                                           Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.reservationPort = Objects.requireNonNull(reservationPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public Order execute(OrderId orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));

        Instant now = clock.instant();
        order.markPaid(Actor.system(), now); // throws InvalidOrderTransitionException if not PLACED

        reservationPort.commit(order.getReservationId());

        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderPaidEvent(saved.getId(), saved.getCustomerId(),
                saved.getReservationId(), now));

        return saved;
    }
}

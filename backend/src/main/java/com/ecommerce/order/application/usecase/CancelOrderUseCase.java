package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Customer-initiated cancellation of their own order, legal only while
 * {@code PLACED|PAID|CONFIRMED} — "until shipped" (rule 7). Ownership-scoped lookup (404 if not
 * theirs, security-architecture §3.2/§3.3). Backs
 * {@code POST /api/v1/orders/{id}/cancellation}.
 *
 * <p>Releases the reservation synchronously via {@link ReservationPort#release} in this same
 * transaction (ADR-0004 §Decision item 3) — one owner of the release call, no competing listener.
 *
 * <p><strong>Does NOT write {@link com.ecommerce.order.application.port.AuditLogPort}</strong> —
 * that port is for admin actions only (security §6c/rule 11). This is a customer action; the
 * {@code order_status_history} row this transition appends is the record for it, written
 * regardless of actor (rule 12).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final ReservationPort reservationPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancelOrderUseCase(OrderRepository orderRepository,
                              ReservationPort reservationPort,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.reservationPort = Objects.requireNonNull(reservationPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public Order execute(OrderId orderId, CustomerId customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "No order " + orderId + " for the current customer"));

        Instant now = clock.instant();
        order.cancel(Actor.customer(customerId.value()), now); // throws InvalidOrderTransitionException if not cancellable

        reservationPort.release(order.getReservationId());

        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCancelledEvent(saved.getId(), saved.getCustomerId(),
                saved.getReservationId(), now));

        return saved;
    }
}

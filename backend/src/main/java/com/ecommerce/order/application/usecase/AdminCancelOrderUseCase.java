package com.ecommerce.order.application.usecase;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.application.port.OrderAuditAction;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin lifecycle transition to {@code CANCELLED} — admin order-lifecycle management
 * (security-architecture §3.4 admin row), legal only while
 * {@code PLACED|PAID|CONFIRMED} (rule 7). Backs
 * {@code POST /api/v1/admin/orders/{id}/cancellation}.
 *
 * <p>Releases the reservation synchronously via {@link ReservationPort#release} in this same
 * transaction (ADR-0004 §Decision item 3), then writes one {@link AuditLogPort} row (security §6c
 * requirement 3) — unlike {@link CancelOrderUseCase} (customer self-cancel, which deliberately
 * does NOT audit-log), this admin variant always does.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class AdminCancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final ReservationPort reservationPort;
    private final AuditLogPort auditLogPort;
    private final CurrentActorPort currentActorPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AdminCancelOrderUseCase(OrderRepository orderRepository,
                                   ReservationPort reservationPort,
                                   AuditLogPort auditLogPort,
                                   CurrentActorPort currentActorPort,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.reservationPort = Objects.requireNonNull(reservationPort);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
        this.currentActorPort = Objects.requireNonNull(currentActorPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Order execute(OrderId orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));

        UUID actorId = currentActorPort.currentActorId();
        OrderStatus statusBefore = order.getStatus();
        Instant now = clock.instant();
        order.cancel(Actor.admin(actorId), now); // throws InvalidOrderTransitionException if not cancellable

        reservationPort.release(order.getReservationId());

        Order saved = orderRepository.save(order);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("statusBefore", statusBefore.name());
        details.put("statusAfter", saved.getStatus().name());
        auditLogPort.record(OrderAuditAction.ORDER_ADMIN_CANCELLED, "Order", orderId.toString(), details);

        eventPublisher.publishEvent(new OrderCancelledEvent(saved.getId(), saved.getCustomerId(),
                saved.getReservationId(), now));

        return saved;
    }
}

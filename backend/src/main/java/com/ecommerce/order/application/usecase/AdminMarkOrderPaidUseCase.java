package com.ecommerce.order.application.usecase;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.application.port.OrderAuditAction;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderPaidEvent;
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
 * Admin lifecycle transition {@code PLACED -> PAID}. Backs
 * {@code POST /api/v1/admin/orders/{id}/payment-confirmation}.
 *
 * <p>Commits the reservation synchronously via {@link ReservationPort#commit} in this same
 * transaction (ADR-0004 §Decision item 3) — the stock stays decremented for good. Writes one
 * {@link AuditLogPort} row in the same transaction as the mutation (security §6c requirement 3).
 *
 * <p>Auth: {@code @PreAuthorize("hasRole('ADMIN')")} — second layer behind the
 * {@code /api/v1/admin/**} route rule (security-architecture §3.2).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class AdminMarkOrderPaidUseCase {

    private final OrderRepository orderRepository;
    private final ReservationPort reservationPort;
    private final AuditLogPort auditLogPort;
    private final CurrentActorPort currentActorPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AdminMarkOrderPaidUseCase(OrderRepository orderRepository,
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
        order.markPaid(Actor.admin(actorId), now); // throws InvalidOrderTransitionException if not PLACED

        reservationPort.commit(order.getReservationId());

        Order saved = orderRepository.save(order);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("statusBefore", statusBefore.name());
        details.put("statusAfter", saved.getStatus().name());
        auditLogPort.record(OrderAuditAction.ORDER_ADMIN_MARKED_PAID, "Order", orderId.toString(), details);

        eventPublisher.publishEvent(new OrderPaidEvent(saved.getId(), saved.getCustomerId(),
                saved.getReservationId(), now));

        return saved;
    }
}

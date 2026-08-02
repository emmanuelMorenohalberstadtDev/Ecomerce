package com.ecommerce.order.application.usecase;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.application.port.OrderAuditAction;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderDeliveredEvent;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
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
 * Admin lifecycle transition {@code SHIPPED -> DELIVERED}. Backs
 * {@code POST /api/v1/admin/orders/{id}/delivery}.
 *
 * <p>Writes one {@link AuditLogPort} row in the same transaction as the mutation (security §6c
 * requirement 3), then publishes {@link OrderDeliveredEvent} (rule 12; no consumers yet).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class AdminDeliverOrderUseCase {

    private final OrderRepository orderRepository;
    private final AuditLogPort auditLogPort;
    private final CurrentActorPort currentActorPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AdminDeliverOrderUseCase(OrderRepository orderRepository,
                                    AuditLogPort auditLogPort,
                                    CurrentActorPort currentActorPort,
                                    ApplicationEventPublisher eventPublisher,
                                    Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
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
        order.deliver(Actor.admin(actorId), now); // throws InvalidOrderTransitionException if not SHIPPED

        Order saved = orderRepository.save(order);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("statusBefore", statusBefore.name());
        details.put("statusAfter", saved.getStatus().name());
        auditLogPort.record(OrderAuditAction.ORDER_ADMIN_DELIVERED, "Order", orderId.toString(), details);

        eventPublisher.publishEvent(new OrderDeliveredEvent(saved.getId(), saved.getCustomerId(), now));

        return saved;
    }
}

package com.ecommerce.order.application.usecase;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.application.port.OrderAuditAction;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.shared.id.OrderId;
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
 * Admin lifecycle transition {@code PAID -> CONFIRMED}. Backs
 * {@code POST /api/v1/admin/orders/{id}/confirmation}.
 *
 * <p><strong>No domain event published:</strong> domain-model.md §4's event catalog lists
 * {@code OrderPlacedEvent}/{@code OrderPaidEvent}/{@code OrderCancelledEvent}/
 * {@code OrderFailedEvent}/{@code OrderShippedEvent}/{@code OrderDeliveredEvent} — no
 * {@code OrderConfirmedEvent} is cataloged, checked deliberately (mirrors
 * {@code CommitReservationUseCase}'s "no domain event published" precedent for the same reason:
 * not every transition needs one). {@code confirm} has no reservation-side effect either — the
 * reservation was already committed at {@code markPaid}.
 *
 * <p>Writes one {@link AuditLogPort} row in the same transaction as the mutation (security §6c
 * requirement 3).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class AdminConfirmOrderUseCase {

    private final OrderRepository orderRepository;
    private final AuditLogPort auditLogPort;
    private final CurrentActorPort currentActorPort;
    private final Clock clock;

    public AdminConfirmOrderUseCase(OrderRepository orderRepository,
                                    AuditLogPort auditLogPort,
                                    CurrentActorPort currentActorPort,
                                    Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
        this.currentActorPort = Objects.requireNonNull(currentActorPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Order execute(OrderId orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));

        UUID actorId = currentActorPort.currentActorId();
        OrderStatus statusBefore = order.getStatus();
        Instant now = clock.instant();
        order.confirm(Actor.admin(actorId), now); // throws InvalidOrderTransitionException if not PAID

        Order saved = orderRepository.save(order);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("statusBefore", statusBefore.name());
        details.put("statusAfter", saved.getStatus().name());
        auditLogPort.record(OrderAuditAction.ORDER_ADMIN_CONFIRMED, "Order", orderId.toString(), details);

        return saved;
    }
}

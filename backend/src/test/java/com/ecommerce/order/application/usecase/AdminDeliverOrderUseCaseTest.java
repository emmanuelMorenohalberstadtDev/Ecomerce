package com.ecommerce.order.application.usecase;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.application.port.OrderAuditAction;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderDeliveredEvent;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminDeliverOrderUseCase} — {@code SHIPPED -> DELIVERED}.
 *
 * <p>Mocks: {@link OrderRepository}, {@link AuditLogPort}, {@link CurrentActorPort},
 * {@link ApplicationEventPublisher}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AdminDeliverOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private CurrentActorPort currentActorPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private AdminDeliverOrderUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final OrderId orderId = OrderId.generate();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AdminDeliverOrderUseCase(orderRepository, auditLogPort, currentActorPort, eventPublisher, clock);
    }

    private Order shippedOrder() {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        Order order = Order.place(orderId, customerId, List.of(line), totals, null, ReservationId.generate(),
                Actor.system(), clock.instant().minusSeconds(60));
        order.markPaid(Actor.admin(actorId), clock.instant());
        order.confirm(Actor.admin(actorId), clock.instant());
        order.ship(Actor.admin(actorId), clock.instant());
        return order;
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void execute_transitionsOrderToDelivered_andSaves() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(shippedOrder()));
        when(currentActorPort.currentActorId()).thenReturn(actorId);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.execute(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void execute_writesExactlyOneAuditLogRow_withStatusBeforeAndAfter() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(shippedOrder()));
        when(currentActorPort.currentActorId()).thenReturn(actorId);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogPort).record(eq(OrderAuditAction.ORDER_ADMIN_DELIVERED), eq("Order"),
                eq(orderId.toString()), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("statusBefore", "SHIPPED");
        assertThat(detailsCaptor.getValue()).containsEntry("statusAfter", "DELIVERED");
    }

    @Test
    void execute_publishesOrderDeliveredEvent() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(shippedOrder()));
        when(currentActorPort.currentActorId()).thenReturn(actorId);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId);

        ArgumentCaptor<OrderDeliveredEvent> captor = ArgumentCaptor.forClass(OrderDeliveredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
    }

    // ── not found ──────────────────────────────────────────────────────────

    @Test
    void execute_throwsOrderNotFoundException_whenOrderDoesNotExist() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId)).isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(auditLogPort, eventPublisher);
        verify(orderRepository, never()).save(any());
    }

    // ── illegal transition: audit row never commits without the mutation ────

    @Test
    void execute_throwsInvalidOrderTransitionException_whenOrderNotShipped_beforeAnySaveOrAudit() {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        Order stillPlaced = Order.place(orderId, customerId, List.of(line), totals, null, ReservationId.generate(),
                Actor.system(), clock.instant());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(stillPlaced));

        assertThatThrownBy(() -> useCase.execute(orderId)).isInstanceOf(InvalidOrderTransitionException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(auditLogPort, eventPublisher);
    }
}

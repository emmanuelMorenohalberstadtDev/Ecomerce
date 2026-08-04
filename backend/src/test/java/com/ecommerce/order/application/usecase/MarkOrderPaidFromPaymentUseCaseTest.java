package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderPaidEvent;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.ActorType;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.order.domain.port.out.ReservationPort;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarkOrderPaidFromPaymentUseCase} — {@code PLACED -> PAID} on a successful
 * payment capture, backing {@code order.application.port.OrderPaymentPort#markPaid} (ADR-0005
 * §Decision item 1).
 *
 * <p>Mocks: {@link OrderRepository}, {@link ReservationPort}, {@link ApplicationEventPublisher}.
 * No {@link com.ecommerce.order.application.port.AuditLogPort}/{@link com.ecommerce.order.application.port.CurrentActorPort}
 * — unlike {@link AdminMarkOrderPaidUseCaseTest}, this use case writes no audit row and resolves no
 * current actor (system-actor transition, ADR-0005 §Decision item 1).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarkOrderPaidFromPaymentUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReservationPort reservationPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC);

    private MarkOrderPaidFromPaymentUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();
    private final OrderId orderId = OrderId.generate();

    @BeforeEach
    void setUp() {
        useCase = new MarkOrderPaidFromPaymentUseCase(orderRepository, reservationPort, eventPublisher, clock);
    }

    private Order placedOrder() {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        return Order.place(orderId, customerId, List.of(line), totals, null, reservationId, Actor.system(),
                clock.instant().minusSeconds(60));
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void execute_transitionsOrderToPaid_andSaves() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(placedOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.execute(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void execute_recordsTheTransitionWithASystemActor() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(placedOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.execute(orderId);

        assertThat(result.getLatestTransition().actor().type()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void execute_commitsTheReservation() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(placedOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId);

        verify(reservationPort).commit(reservationId);
    }

    @Test
    void execute_publishesOrderPaidEvent() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(placedOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId);

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().reservationId()).isEqualTo(reservationId);
    }

    // ── not found ──────────────────────────────────────────────────────────

    @Test
    void execute_throwsOrderNotFoundException_whenOrderDoesNotExist() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId)).isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(reservationPort, eventPublisher);
        verify(orderRepository, never()).save(any());
    }

    // ── illegal transition ────────────────────────────────────────────────

    @Test
    void execute_throwsInvalidOrderTransitionException_whenOrderNotPlaced_beforeAnySaveOrCommit() {
        Order alreadyPaid = placedOrder();
        alreadyPaid.markPaid(Actor.admin(UUID.randomUUID()), clock.instant());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(alreadyPaid));

        assertThatThrownBy(() -> useCase.execute(orderId)).isInstanceOf(InvalidOrderTransitionException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(reservationPort, eventPublisher);
    }
}

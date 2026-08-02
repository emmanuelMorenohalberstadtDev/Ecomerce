package com.ecommerce.order.application.usecase;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CancelOrderUseCase} — customer self-cancel.
 *
 * <p>Mocks: {@link OrderRepository}, {@link ReservationPort}, {@link ApplicationEventPublisher}.
 * No Spring context.
 *
 * <p><strong>"Does NOT write {@link AuditLogPort}" (security-review-verified property):</strong>
 * this use case's constructor has no {@link AuditLogPort} parameter at all — a stronger,
 * compile-time guarantee than a runtime mock verification could give (there is no port reference
 * to accidentally invoke). {@link #execute_hasNoAuditLogPortDependency_soItCanNeverBeCalled()}
 * asserts that structural fact via reflection, locking the property in so it cannot silently
 * regress if a future edit adds the dependency without updating this test.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CancelOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReservationPort reservationPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private CancelOrderUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();
    private final OrderId orderId = OrderId.generate();

    @BeforeEach
    void setUp() {
        useCase = new CancelOrderUseCase(orderRepository, reservationPort, eventPublisher, clock);
    }

    private Order orderIn(OrderStatus status) {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        Order order = Order.place(orderId, customerId, List.of(line), totals, null, reservationId,
                Actor.system(), clock.instant().minusSeconds(3600));
        switch (status) {
            case PLACED -> { }
            case PAID -> order.markPaid(Actor.admin(null), clock.instant());
            case CONFIRMED -> {
                order.markPaid(Actor.admin(null), clock.instant());
                order.confirm(Actor.admin(null), clock.instant());
            }
            case SHIPPED -> {
                order.markPaid(Actor.admin(null), clock.instant());
                order.confirm(Actor.admin(null), clock.instant());
                order.ship(Actor.admin(null), clock.instant());
            }
            case DELIVERED -> {
                order.markPaid(Actor.admin(null), clock.instant());
                order.confirm(Actor.admin(null), clock.instant());
                order.ship(Actor.admin(null), clock.instant());
                order.deliver(Actor.admin(null), clock.instant());
            }
            case CANCELLED -> order.cancel(Actor.customer(customerId.value()), clock.instant());
            case FAILED -> order.fail(Actor.system(), clock.instant());
        }
        return order;
    }

    // ── success from every cancellable state ────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PLACED", "PAID", "CONFIRMED"})
    void execute_cancelsOrder_fromEveryCancellableState(OrderStatus fromStatus) {
        Order order = orderIn(fromStatus);
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.execute(orderId, customerId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getLatestTransition().from()).isEqualTo(fromStatus);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PLACED", "PAID", "CONFIRMED"})
    void execute_releasesTheReservation_fromEveryCancellableState(OrderStatus fromStatus) {
        Order order = orderIn(fromStatus);
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId, customerId);

        verify(reservationPort).release(reservationId);
    }

    @Test
    void execute_publishesOrderCancelledEvent() {
        Order order = orderIn(OrderStatus.PLACED);
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId, customerId);

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
        assertThat(captor.getValue().reservationId()).isEqualTo(reservationId);
    }

    // ── rejected once shipped or terminal ───────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELLED", "FAILED"})
    void execute_throwsInvalidOrderTransitionException_whenNoLongerCancellable(OrderStatus fromStatus) {
        Order order = orderIn(fromStatus);
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(orderId, customerId))
                .isInstanceOf(InvalidOrderTransitionException.class);

        verify(reservationPort, never()).release(any());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── ownership: wrong customer is 404, not a transition error ───────────

    @Test
    void execute_throwsOrderNotFoundException_whenOrderDoesNotBelongToCaller() {
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId, customerId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(reservationPort, never()).release(any());
        verify(orderRepository, never()).save(any());
    }

    // ── security-review-verified property: no AuditLogPort dependency at all ──

    @Test
    void execute_hasNoAuditLogPortDependency_soItCanNeverBeCalled() {
        Constructor<?>[] constructors = CancelOrderUseCase.class.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            assertThat(constructor.getParameterTypes()).doesNotContain(AuditLogPort.class);
        }
        Field[] fields = CancelOrderUseCase.class.getDeclaredFields();
        assertThat(Arrays.stream(fields).map(Field::getType)).doesNotContain(AuditLogPort.class);
    }
}

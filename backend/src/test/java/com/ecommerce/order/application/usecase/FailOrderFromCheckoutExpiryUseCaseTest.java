package com.ecommerce.order.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutSessionExpiredEvent;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderFailedEvent;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.ActorType;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.shared.id.CheckoutSessionId;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FailOrderFromCheckoutExpiryUseCase}.
 *
 * <p>Mocks: {@link OrderRepository}, {@link ApplicationEventPublisher}. No Spring context.
 *
 * <p><strong>"Must NOT call {@code ReservationPort.release}" (ADR-0004 sanity check):</strong> this
 * use case's constructor has no {@link ReservationPort} parameter at all — a stronger, compile-time
 * guarantee than a runtime mock verification could ever give (there is no port reference to
 * accidentally invoke). {@link #execute_hasNoReservationPortDependency_soReleaseCanNeverBeCalled()}
 * asserts that structural fact via reflection, which is what the security-review property actually
 * reduces to for this class; every other use case in this suite that DOES hold a
 * {@link ReservationPort} verifies the call/non-call with a real mock instead.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FailOrderFromCheckoutExpiryUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private FailOrderFromCheckoutExpiryUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();

    @BeforeEach
    void setUp() {
        useCase = new FailOrderFromCheckoutExpiryUseCase(orderRepository, eventPublisher, clock);
    }

    private CheckoutSessionExpiredEvent expiredEvent() {
        return new CheckoutSessionExpiredEvent(CheckoutSessionId.generate(), customerId, reservationId,
                clock.instant());
    }

    private Order placedOrder() {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        return Order.place(OrderId.generate(), customerId, List.of(line), totals, null, reservationId,
                Actor.system(), clock.instant().minusSeconds(900));
    }

    // ── happy path: PLACED -> FAILED ────────────────────────────────────────

    @Test
    void shouldTransitionExistingPlacedOrder_toFailed_withActorTypeSystem() {
        Order order = placedOrder();
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(expiredEvent());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(saved.getLatestTransition().actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getLatestTransition().from()).isEqualTo(OrderStatus.PLACED);
        assertThat(saved.getLatestTransition().to()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void shouldPublishOrderFailedEvent_withOrderCustomerAndReservationIds() {
        Order order = placedOrder();
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(expiredEvent());

        ArgumentCaptor<OrderFailedEvent> captor = ArgumentCaptor.forClass(OrderFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(order.getId());
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
        assertThat(captor.getValue().reservationId()).isEqualTo(reservationId);
    }

    // ── "no order yet" is a graceful no-op, not an exception ───────────────

    @Test
    void shouldNoOpGracefully_whenNoOrderExistsYetForTheReservation() {
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.execute(expiredEvent())).doesNotThrowAnyException();

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── legitimate race: order already moved on (e.g. paid) before this fired ──

    @Test
    void shouldSkipGracefully_whenOrderIsNoLongerPlaced() {
        Order order = placedOrder();
        order.markPaid(Actor.admin(null), clock.instant());
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.of(order));

        assertThatCode(() -> useCase.execute(expiredEvent())).doesNotThrowAnyException();

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── ADR-0004 sanity check: no ReservationPort dependency exists to call release on ──

    @Test
    void execute_hasNoReservationPortDependency_soReleaseCanNeverBeCalled() {
        Constructor<?>[] constructors = FailOrderFromCheckoutExpiryUseCase.class.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            assertThat(constructor.getParameterTypes()).doesNotContain(ReservationPort.class);
        }
        Field[] fields = FailOrderFromCheckoutExpiryUseCase.class.getDeclaredFields();
        assertThat(Arrays.stream(fields).map(Field::getType)).doesNotContain(ReservationPort.class);
    }
}

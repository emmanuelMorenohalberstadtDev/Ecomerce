package com.ecommerce.order.application.usecase;

import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.event.OrderPlacedEvent;
import com.ecommerce.order.domain.exception.DuplicateOrderException;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.out.ProductCatalogPort;
import com.ecommerce.order.domain.port.out.ProductCatalogPort.CatalogProductView;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlaceOrderFromCheckoutUseCase}.
 *
 * <p>Mocks: {@link OrderRepository}, {@link ProductCatalogPort}, {@link ApplicationEventPublisher}.
 * No Spring context — the class is constructed directly, mirroring
 * {@code checkout.application.usecase.StartCheckoutUseCaseTest}'s style.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PlaceOrderFromCheckoutUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductCatalogPort productCatalogPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private PlaceOrderFromCheckoutUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new PlaceOrderFromCheckoutUseCase(orderRepository, productCatalogPort, eventPublisher, clock);
    }

    private CheckoutAwaitingPaymentEvent eventWithOneLine() {
        Money unitPrice = new Money(new BigDecimal("10.00"), "USD");
        Money lineTotal = new Money(new BigDecimal("20.00"), "USD");
        CheckoutAwaitingPaymentEvent.Line line =
                new CheckoutAwaitingPaymentEvent.Line(productId, Quantity.of(2), unitPrice, lineTotal);
        return new CheckoutAwaitingPaymentEvent(CheckoutSessionId.generate(), customerId, CartId.generate(),
                reservationId, List.of(line), Money.zero("USD"), new Money(new BigDecimal("5.00"), "USD"),
                new Money(new BigDecimal("25.00"), "USD"), clock.instant().plusSeconds(900), clock.instant());
    }

    private void noExistingOrderForReservation() {
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void shouldCreateOrderInPlaced_withLineSnapshotsBuiltFromEventLines() {
        noExistingOrderForReservation();
        when(productCatalogPort.findActiveProduct(productId))
                .thenReturn(Optional.of(new CatalogProductView(productId, "Widget", new Money(new BigDecimal("10.00"), "USD"))));
        when(orderRepository.create(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(eventWithOneLine());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).create(captor.capture());
        Order created = captor.getValue();
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(created.getCustomerId()).isEqualTo(customerId);
        assertThat(created.getReservationId()).isEqualTo(reservationId);
        assertThat(created.getLines()).hasSize(1);
        LineSnapshot line = created.getLines().get(0);
        assertThat(line.productId()).isEqualTo(productId);
        assertThat(line.productName()).isEqualTo("Widget");
        assertThat(line.unitPrice()).isEqualTo(new Money(new BigDecimal("10.00"), "USD"));
        assertThat(line.quantity()).isEqualTo(Quantity.of(2));
    }

    @Test
    void shouldComputeItemsTotalFromLines_andCarryDiscountShippingGrandTotalsFromEvent() {
        noExistingOrderForReservation();
        when(productCatalogPort.findActiveProduct(productId))
                .thenReturn(Optional.of(new CatalogProductView(productId, "Widget", new Money(new BigDecimal("10.00"), "USD"))));
        when(orderRepository.create(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(eventWithOneLine());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).create(captor.capture());
        assertThat(captor.getValue().getTotals().itemsTotal()).isEqualTo(new Money(new BigDecimal("20.00"), "USD"));
        assertThat(captor.getValue().getTotals().discountTotal()).isEqualTo(Money.zero("USD"));
        assertThat(captor.getValue().getTotals().shippingTotal()).isEqualTo(new Money(new BigDecimal("5.00"), "USD"));
        assertThat(captor.getValue().getTotals().grandTotal()).isEqualTo(new Money(new BigDecimal("25.00"), "USD"));
    }

    @Test
    void shouldPublishOrderPlacedEvent_withSavedOrderIdCustomerIdAndReservationId() {
        noExistingOrderForReservation();
        when(productCatalogPort.findActiveProduct(productId))
                .thenReturn(Optional.of(new CatalogProductView(productId, "Widget", new Money(new BigDecimal("10.00"), "USD"))));
        when(orderRepository.create(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(eventWithOneLine());

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
        assertThat(captor.getValue().reservationId()).isEqualTo(reservationId);
    }

    // ── idempotency: up-front findByReservationId short-circuit ────────────

    @Test
    void shouldNotCreateOrder_whenOrderAlreadyExistsForReservation() {
        Order existing = Order.place(com.ecommerce.shared.id.OrderId.generate(), customerId,
                List.of(new LineSnapshot(productId, "Widget", new Money(new BigDecimal("10.00"), "USD"), Quantity.of(1))),
                new com.ecommerce.order.domain.model.OrderTotals(new Money(new BigDecimal("10.00"), "USD"),
                        Money.zero("USD"), Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD")),
                null, reservationId, com.ecommerce.order.domain.model.Actor.system(), clock.instant());
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.of(existing));

        useCase.execute(eventWithOneLine());

        verify(orderRepository, never()).create(any());
        verifyNoInteractions(productCatalogPort);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── idempotency: DB-unique-constraint race backstop ─────────────────────

    @Test
    void shouldNoOp_whenCreateLosesTheRaceAgainstAnotherAfterCommitFiring() {
        noExistingOrderForReservation();
        when(productCatalogPort.findActiveProduct(productId))
                .thenReturn(Optional.of(new CatalogProductView(productId, "Widget", new Money(new BigDecimal("10.00"), "USD"))));
        when(orderRepository.create(any(Order.class)))
                .thenThrow(new DuplicateOrderException("already exists"));

        assertThatCode(() -> useCase.execute(eventWithOneLine())).doesNotThrowAnyException();

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── retired-product fallback ────────────────────────────────────────────

    @Test
    void shouldFallBackToPlaceholderName_andNotThrow_whenProductCatalogPortReturnsEmpty() {
        noExistingOrderForReservation();
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.empty());
        when(orderRepository.create(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> useCase.execute(eventWithOneLine())).doesNotThrowAnyException();

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).create(captor.capture());
        assertThat(captor.getValue().getLines().get(0).productName()).isEqualTo("Unavailable product " + productId);
    }
}

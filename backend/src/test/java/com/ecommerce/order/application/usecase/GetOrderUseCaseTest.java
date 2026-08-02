package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetOrderUseCase} — ownership-scoped read, mirroring
 * {@code checkout.application.usecase.GetCheckoutSessionUseCaseTest}'s style for the equivalent
 * single-resource read use case.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GetOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    private GetOrderUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final OrderId orderId = OrderId.generate();

    @BeforeEach
    void setUp() {
        useCase = new GetOrderUseCase(orderRepository);
    }

    private Order order() {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        return Order.place(orderId, customerId, List.of(line), totals, null, ReservationId.generate(),
                Actor.system(), Instant.parse("2026-07-31T10:00:00Z"));
    }

    @Test
    void shouldReturnOrder_whenOwnedByCaller() {
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order()));

        Order result = useCase.execute(orderId, customerId);

        assertThat(result.getId()).isEqualTo(orderId);
    }

    @Test
    void shouldThrowOrderNotFoundException_whenOrderDoesNotExist() {
        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId, customerId)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldThrowOrderNotFoundException_whenOrderBelongsToAnotherCustomer() {
        CustomerId anotherCustomer = CustomerId.generate();
        when(orderRepository.findByIdAndCustomerId(orderId, anotherCustomer)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId, anotherCustomer)).isInstanceOf(OrderNotFoundException.class);
    }
}

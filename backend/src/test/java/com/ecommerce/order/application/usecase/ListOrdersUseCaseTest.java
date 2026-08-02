package com.ecommerce.order.application.usecase;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.OrderRepository.OrderSummary;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListOrdersUseCase} — paginated order history for the current customer.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ListOrdersUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    private ListOrdersUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();

    @BeforeEach
    void setUp() {
        useCase = new ListOrdersUseCase(orderRepository);
    }

    @Test
    void shouldDelegateToRepository_andReturnThePageAsIs() {
        OrderSummary summary = new OrderSummary(OrderId.generate(), "PLACED",
                new Money(new BigDecimal("20.00"), "USD"), Instant.parse("2026-07-31T10:00:00Z"));
        PageResult<OrderSummary> page = new PageResult<>(List.of(summary), 0, 20, 1, 1);
        when(orderRepository.findByCustomerId(customerId, 0, 20)).thenReturn(page);

        PageResult<OrderSummary> result = useCase.execute(customerId, 0, 20);

        assertThat(result).isSameAs(page);
        assertThat(result.content()).containsExactly(summary);
    }

    @Test
    void shouldPassPageAndSizeArgumentsThrough_untouched() {
        PageResult<OrderSummary> emptyPage = new PageResult<>(List.of(), 2, 10, 0, 0);
        when(orderRepository.findByCustomerId(customerId, 2, 10)).thenReturn(emptyPage);

        useCase.execute(customerId, 2, 10);

        // Verifies the exact (page, size) pair reached the repository unclamped — clamping is the
        // presentation layer's job (OrderController), per this use case's own class javadoc.
        org.mockito.Mockito.verify(orderRepository).findByCustomerId(customerId, 2, 10);
    }
}

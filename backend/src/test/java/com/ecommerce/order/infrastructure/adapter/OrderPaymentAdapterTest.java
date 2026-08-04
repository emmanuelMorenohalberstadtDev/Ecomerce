package com.ecommerce.order.infrastructure.adapter;

import com.ecommerce.order.application.port.OrderPaymentPort;
import com.ecommerce.order.application.port.OrderPaymentPort.OrderPaymentView;
import com.ecommerce.order.application.usecase.GetOrderForPaymentUseCase;
import com.ecommerce.order.application.usecase.MarkOrderPaidFromPaymentUseCase;
import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OrderPaymentAdapter} — translates {@link GetOrderForPaymentUseCase}/
 * {@link MarkOrderPaidFromPaymentUseCase}'s own domain exceptions (private to order's
 * {@code domain.exception} package) into this façade's nested exception types (ADR-0005 §Decision
 * item 1), mirroring {@code inventory.infrastructure.adapter.StockReservationAdapterTest}'s pattern
 * for the equivalent producer-side façade adapter.
 *
 * <p>Note on naming: order's own {@code domain.exception.OrderNotFoundException} shares its simple
 * name with {@link OrderPaymentPort}'s nested {@code OrderNotFoundException} — the two cannot both
 * be imported unqualified in one file, so the domain-layer one (what
 * {@link GetOrderForPaymentUseCase}/{@link MarkOrderPaidFromPaymentUseCase} actually throw) is
 * referenced fully-qualified below; the façade-level one (what the adapter must translate it into)
 * is imported via {@link OrderPaymentPort}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderPaymentAdapterTest {

    @Mock
    private GetOrderForPaymentUseCase getOrderForPaymentUseCase;

    @Mock
    private MarkOrderPaidFromPaymentUseCase markOrderPaidFromPaymentUseCase;

    private OrderPaymentAdapter adapter;

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();
    private final OrderId orderId = OrderId.generate();

    @BeforeEach
    void setUp() {
        adapter = new OrderPaymentAdapter(getOrderForPaymentUseCase, markOrderPaidFromPaymentUseCase);
    }

    private Order placedOrder() {
        LineSnapshot line = new LineSnapshot(ProductId.generate(), "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(1));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("10.00"), "USD"), Money.zero("USD"),
                Money.zero("USD"), new Money(new BigDecimal("10.00"), "USD"));
        return Order.place(orderId, customerId, List.of(line), totals, null, reservationId, Actor.system(),
                Instant.parse("2026-08-02T10:00:00Z"));
    }

    // ── getForPayment() ─────────────────────────────────────────────────────

    @Test
    void getForPayment_returnsMappedView_whenUseCaseSucceeds() {
        when(getOrderForPaymentUseCase.execute(orderId, customerId)).thenReturn(placedOrder());

        OrderPaymentView result = adapter.getForPayment(orderId, customerId);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(result.grandTotal()).isEqualTo(new Money(new BigDecimal("10.00"), "USD"));
        assertThat(result.reservationId()).isEqualTo(reservationId);
    }

    @Test
    void getForPayment_throwsFacadesOrderNotFoundException_whenUseCaseReportsOrderNotFound() {
        when(getOrderForPaymentUseCase.execute(orderId, customerId))
                .thenThrow(new com.ecommerce.order.domain.exception.OrderNotFoundException("no such order"));

        assertThatThrownBy(() -> adapter.getForPayment(orderId, customerId))
                .isInstanceOf(OrderPaymentPort.OrderNotFoundException.class);
    }

    // ── markPaid() ───────────────────────────────────────────────────────────

    @Test
    void markPaid_delegatesToMarkOrderPaidFromPaymentUseCase() {
        adapter.markPaid(orderId);

        verify(markOrderPaidFromPaymentUseCase).execute(orderId);
    }

    @Test
    void markPaid_throwsFacadesOrderNotFoundException_whenUseCaseReportsOrderNotFound() {
        doThrow(new com.ecommerce.order.domain.exception.OrderNotFoundException("no such order"))
                .when(markOrderPaidFromPaymentUseCase).execute(orderId);

        assertThatThrownBy(() -> adapter.markPaid(orderId))
                .isInstanceOf(OrderPaymentPort.OrderNotFoundException.class);
    }

    @Test
    void markPaid_throwsOrderNotPayableException_whenOrderIsNotPlaced() {
        doThrow(new InvalidOrderTransitionException("order not PLACED"))
                .when(markOrderPaidFromPaymentUseCase).execute(orderId);

        assertThatThrownBy(() -> adapter.markPaid(orderId))
                .isInstanceOf(OrderPaymentPort.OrderNotPayableException.class);
    }
}

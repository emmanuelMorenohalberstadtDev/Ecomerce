package com.ecommerce.payment.infrastructure.adapter;

import com.ecommerce.order.application.port.OrderPaymentPort;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.payment.domain.exception.OrderNotFoundException;
import com.ecommerce.payment.domain.exception.OrderNotPayableException;
import com.ecommerce.payment.domain.port.out.OrderPort.OrderView;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OrderAdapter} — translates order's façade-owned {@link OrderPaymentPort}
 * view/exceptions into payment's own {@link OrderView}/domain exceptions (ADR-0005 §Decision item
 * 1), mirroring {@code checkout.infrastructure.adapter.ReservationAdapterTest}'s pattern for the
 * equivalent thin cross-context adapter.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderAdapterTest {

    @Mock
    private OrderPaymentPort orderPaymentPort;

    private OrderAdapter adapter;

    private final OrderId orderId = OrderId.generate();
    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();
    private final Money grandTotal = new Money(new BigDecimal("25.00"), "USD");

    @BeforeEach
    void setUp() {
        adapter = new OrderAdapter(orderPaymentPort);
    }

    // ── getForPayment() ─────────────────────────────────────────────────────

    @Test
    void getForPayment_mapsPayableTrue_whenOrderStatusIsPlaced() {
        when(orderPaymentPort.getForPayment(orderId, customerId)).thenReturn(
                new OrderPaymentPort.OrderPaymentView(orderId, customerId, OrderStatus.PLACED, grandTotal, reservationId));

        OrderView result = adapter.getForPayment(orderId, customerId);

        assertThat(result.payable()).isTrue();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.grandTotal()).isEqualTo(grandTotal);
        assertThat(result.reservationId()).isEqualTo(reservationId);
    }

    @Test
    void getForPayment_mapsPayableFalse_whenOrderStatusIsNotPlaced() {
        when(orderPaymentPort.getForPayment(orderId, customerId)).thenReturn(
                new OrderPaymentPort.OrderPaymentView(orderId, customerId, OrderStatus.PAID, grandTotal, reservationId));

        OrderView result = adapter.getForPayment(orderId, customerId);

        assertThat(result.payable()).isFalse();
    }

    @Test
    void getForPayment_throwsPaymentsOrderNotFoundException_whenPortReportsOrderNotFound() {
        when(orderPaymentPort.getForPayment(orderId, customerId))
                .thenThrow(new OrderPaymentPort.OrderNotFoundException("no such order"));

        assertThatThrownBy(() -> adapter.getForPayment(orderId, customerId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── markPaid() ───────────────────────────────────────────────────────────

    @Test
    void markPaid_delegatesToOrderPaymentPort() {
        adapter.markPaid(orderId);

        verify(orderPaymentPort).markPaid(orderId);
    }

    @Test
    void markPaid_throwsPaymentsOrderNotFoundException_whenPortReportsOrderNotFound() {
        doThrow(new OrderPaymentPort.OrderNotFoundException("no such order"))
                .when(orderPaymentPort).markPaid(orderId);

        assertThatThrownBy(() -> adapter.markPaid(orderId)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void markPaid_throwsOrderNotPayableException_whenPortReportsOrderNotPayable() {
        doThrow(new OrderPaymentPort.OrderNotPayableException("order not PLACED"))
                .when(orderPaymentPort).markPaid(orderId);

        assertThatThrownBy(() -> adapter.markPaid(orderId)).isInstanceOf(OrderNotPayableException.class);
    }
}

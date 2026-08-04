package com.ecommerce.payment.application.usecase;

import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.payment.domain.event.PaymentRefundedEvent;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.model.RefundReason;
import com.ecommerce.payment.domain.port.out.PaymentRepository;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IssueRefundOnOrderCancelledUseCase} (ADR-0005 item 4).
 *
 * <p>Mocks: {@link PaymentRepository}, {@link ApplicationEventPublisher}. No Spring context,
 * mirroring {@code order.application.usecase.FailOrderFromCheckoutExpiryUseCaseTest}'s style for
 * the sibling "graceful no-op" use case.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IssueRefundOnOrderCancelledUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC);

    private IssueRefundOnOrderCancelledUseCase useCase;

    private final OrderId orderId = OrderId.generate();
    private final CustomerId customerId = CustomerId.generate();
    private final Money amount = new Money(new BigDecimal("25.00"), "USD");

    @BeforeEach
    void setUp() {
        useCase = new IssueRefundOnOrderCancelledUseCase(paymentRepository, eventPublisher, clock);
    }

    private OrderCancelledEvent event() {
        return new OrderCancelledEvent(orderId, customerId, ReservationId.generate(), clock.instant());
    }

    private Payment pendingPayment() {
        return Payment.initiate(PaymentId.generate(), orderId, customerId, amount, clock.instant().minusSeconds(60));
    }

    private Payment capturedPayment() {
        Payment payment = pendingPayment();
        payment.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", clock.instant().minusSeconds(30));
        payment.markCaptured(clock.instant().minusSeconds(30));
        return payment;
    }

    // ── no-op: no payment exists for the cancelled order ────────────────────

    @Test
    void execute_doesNothing_whenNoPaymentExistsForTheOrder() {
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.execute(event())).doesNotThrowAnyException();

        verify(paymentRepository, never()).appendRefund(any(), any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── no-op: payment exists but is still PENDING (nothing captured yet) ───

    @Test
    void execute_doesNothing_whenPaymentIsStillPending() {
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(pendingPayment()));

        assertThatCode(() -> useCase.execute(event())).doesNotThrowAnyException();

        verify(paymentRepository, never()).appendRefund(any(), any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── no-op: payment already REFUNDED (duplicate event delivery) ──────────

    @Test
    void execute_doesNothing_whenPaymentIsAlreadyRefunded() {
        Payment refunded = capturedPayment();
        refunded.markRefunded(RefundReason.ORDER_CANCELLED, null, clock.instant().minusSeconds(10));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(refunded));

        assertThatCode(() -> useCase.execute(event())).doesNotThrowAnyException();

        verify(paymentRepository, never()).appendRefund(any(), any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── happy path: CAPTURED payment is refunded ────────────────────────────

    @Test
    void execute_refundsTheCapturedPayment_withOrderCancelledReason() {
        Payment captured = capturedPayment();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(captured));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(event());

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<com.ecommerce.payment.domain.model.Refund> refundCaptor =
                ArgumentCaptor.forClass(com.ecommerce.payment.domain.model.Refund.class);
        verify(paymentRepository).appendRefund(any(), refundCaptor.capture());
        assertThat(refundCaptor.getValue().reason()).isEqualTo(RefundReason.ORDER_CANCELLED);
        assertThat(refundCaptor.getValue().gatewayReference()).isNull();
        verify(paymentRepository).save(captured);
    }

    @Test
    void execute_publishesPaymentRefundedEvent() {
        Payment captured = capturedPayment();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(captured));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(event());

        ArgumentCaptor<PaymentRefundedEvent> captor = ArgumentCaptor.forClass(PaymentRefundedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
        assertThat(captor.getValue().amount()).isEqualTo(amount);
    }
}

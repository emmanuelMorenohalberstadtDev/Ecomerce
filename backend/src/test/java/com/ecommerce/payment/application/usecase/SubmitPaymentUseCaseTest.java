package com.ecommerce.payment.application.usecase;

import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase.SubmitPaymentCommand;
import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase.SubmitPaymentResult;
import com.ecommerce.payment.domain.event.PaymentCapturedEvent;
import com.ecommerce.payment.domain.event.PaymentDeclinedEvent;
import com.ecommerce.payment.domain.exception.DuplicatePaymentException;
import com.ecommerce.payment.domain.exception.OrderNotPayableException;
import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.port.out.OrderPort;
import com.ecommerce.payment.domain.port.out.OrderPort.OrderView;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort.ChargeRequest;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort.GatewayOutcome;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubmitPaymentUseCase} (ADR-0005).
 *
 * <p>Mocks: {@link PaymentRepository}, {@link OrderPort}, {@link PaymentGatewayPort},
 * {@link ApplicationEventPublisher}. No Spring context — the class is constructed directly,
 * mirroring {@code order.application.usecase.PlaceOrderFromCheckoutUseCaseTest}'s style.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubmitPaymentUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderPort orderPort;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC);

    private SubmitPaymentUseCase useCase;

    private final OrderId orderId = OrderId.generate();
    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();
    private final Money grandTotal = new Money(new BigDecimal("25.00"), "USD");

    @BeforeEach
    void setUp() {
        useCase = new SubmitPaymentUseCase(paymentRepository, orderPort, paymentGatewayPort, eventPublisher, clock);
    }

    private SubmitPaymentCommand command(String idempotencyKey) {
        return new SubmitPaymentCommand(orderId, customerId, idempotencyKey);
    }

    private OrderView payableOrderView() {
        return new OrderView(orderId, customerId, true, grandTotal, reservationId);
    }

    private OrderView notPayableOrderView() {
        return new OrderView(orderId, customerId, false, grandTotal, reservationId);
    }

    private Payment pendingPayment() {
        return Payment.initiate(PaymentId.generate(), orderId, customerId, grandTotal, clock.instant());
    }

    private void noExistingPayment() {
        when(paymentRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());
    }

    // ── idempotency replay: same key returns the recorded outcome, no gateway call ──

    @Test
    void execute_returnsRecordedOutcome_withoutCallingGatewayOrOrderPort_whenIdempotencyKeyAlreadyRecorded() {
        Payment existing = pendingPayment();
        existing.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1",
                clock.instant());
        when(paymentRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(existing));

        SubmitPaymentResult result = useCase.execute(command("key-1"));

        assertThat(result.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(result.declineReason()).isEqualTo(DeclineReason.CARD_DECLINED);
        verifyNoInteractions(orderPort, paymentGatewayPort, eventPublisher);
        verify(paymentRepository, never()).appendAttempt(any(), any());
        verify(paymentRepository, never()).create(any());
        verify(paymentRepository, never()).save(any());
    }

    // ── fast-fail: order not payable, before the gateway is ever called ─────

    @Test
    void execute_throwsOrderNotPayableException_beforeCallingGateway_whenOrderIsNotPayable() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(notPayableOrderView());

        assertThatThrownBy(() -> useCase.execute(command("key-1")))
                .isInstanceOf(OrderNotPayableException.class);

        verifyNoInteractions(paymentGatewayPort);
        verify(paymentRepository, never()).create(any());
    }

    // ── OrderNotFoundException propagates from OrderPort ────────────────────

    @Test
    void execute_propagatesOrderNotFoundException_whenOrderPortCannotFindTheOrder() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId))
                .thenThrow(new com.ecommerce.payment.domain.exception.OrderNotFoundException("no such order"));

        assertThatThrownBy(() -> useCase.execute(command("key-1")))
                .isInstanceOf(com.ecommerce.payment.domain.exception.OrderNotFoundException.class);

        verifyNoInteractions(paymentGatewayPort);
    }

    // ── DuplicatePaymentException: lost race on create(), before the gateway is called ──

    @Test
    void execute_propagatesDuplicatePaymentException_whenCreateLosesTheUniqueOrderRace() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class)))
                .thenThrow(new DuplicatePaymentException("already exists"));

        assertThatThrownBy(() -> useCase.execute(command("key-1")))
                .isInstanceOf(DuplicatePaymentException.class);

        verifyNoInteractions(paymentGatewayPort);
    }

    // ── APPROVED path: new payment, capture, markPaid, save, publish ────────

    @Test
    void execute_capturesPaymentAndMarksOrderPaid_whenGatewayApproves() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.APPROVED, null, "SIM-ref-1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitPaymentResult result = useCase.execute(command("key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(result.outcome()).isEqualTo(PaymentOutcome.APPROVED);
        verify(orderPort).markPaid(orderId);
        verify(paymentRepository).appendAttempt(any(), any());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void execute_publishesPaymentCapturedEvent_whenGatewayApproves() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.APPROVED, null, "SIM-ref-1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command("key-1"));

        ArgumentCaptor<PaymentCapturedEvent> captor = ArgumentCaptor.forClass(PaymentCapturedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
        assertThat(captor.getValue().amount()).isEqualTo(grandTotal);
    }

    @Test
    void execute_chargesTheOrdersGrandTotal_neverAClientSuppliedAmount() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.APPROVED, null, "SIM-ref-1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command("key-1"));

        ArgumentCaptor<ChargeRequest> captor = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(paymentGatewayPort).charge(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(grandTotal);
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void execute_reusesExistingPendingPayment_onARetryWithANewIdempotencyKey() {
        Payment existing = pendingPayment();
        existing.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1",
                clock.instant());
        when(paymentRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(existing));
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.APPROVED, null, "SIM-ref-2"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitPaymentResult result = useCase.execute(command("key-2"));

        assertThat(result.status()).isEqualTo(PaymentStatus.CAPTURED);
        verify(paymentRepository, never()).create(any());
        verify(orderPort).markPaid(orderId);
    }

    // ── DECLINED path: attempt recorded, payment stays PENDING, order untouched ──

    @Test
    void execute_recordsDeclinedAttempt_leavesPaymentPending_andNeverCallsOrderPort() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.DECLINED, DeclineReason.INSUFFICIENT_FUNDS, "SIM-ref-1"));

        SubmitPaymentResult result = useCase.execute(command("key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(result.declineReason()).isEqualTo(DeclineReason.INSUFFICIENT_FUNDS);
        verify(orderPort, never()).markPaid(any());
        verify(paymentRepository).appendAttempt(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void execute_publishesPaymentDeclinedEvent_whenGatewayDeclines() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.DECLINED, DeclineReason.FRAUD_SUSPECTED, "SIM-ref-1"));

        useCase.execute(command("key-1"));

        ArgumentCaptor<PaymentDeclinedEvent> captor = ArgumentCaptor.forClass(PaymentDeclinedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().declineReason()).isEqualTo(DeclineReason.FRAUD_SUSPECTED);
    }

    // ── TIMEOUT path: same as DECLINED, but no domain event published ────────

    @Test
    void execute_recordsTimeoutAttempt_leavesPaymentPending_andNeverCallsOrderPort() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.TIMEOUT, null, null));

        SubmitPaymentResult result = useCase.execute(command("key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.outcome()).isEqualTo(PaymentOutcome.TIMEOUT);
        verify(orderPort, never()).markPaid(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void execute_publishesNoEvent_whenGatewayTimesOut() {
        noExistingPayment();
        when(orderPort.getForPayment(orderId, customerId)).thenReturn(payableOrderView());
        when(paymentRepository.create(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGatewayPort.charge(any(ChargeRequest.class)))
                .thenReturn(new GatewayOutcome(PaymentOutcome.TIMEOUT, null, null));

        useCase.execute(command("key-1"));

        verifyNoInteractions(eventPublisher);
    }

    // ── SubmitPaymentCommand's own validation ───────────────────────────────

    @Test
    void submitPaymentCommand_throwsIllegalArgumentException_whenIdempotencyKeyIsBlank() {
        assertThatThrownBy(() -> new SubmitPaymentCommand(orderId, customerId, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitPaymentCommand_throwsNullPointerException_whenOrderIdIsNull() {
        assertThatThrownBy(() -> new SubmitPaymentCommand(null, customerId, "key-1"))
                .isInstanceOf(NullPointerException.class);
    }
}

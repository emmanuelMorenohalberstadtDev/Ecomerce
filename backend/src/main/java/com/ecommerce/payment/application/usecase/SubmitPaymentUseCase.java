package com.ecommerce.payment.application.usecase;

import com.ecommerce.payment.domain.event.PaymentCapturedEvent;
import com.ecommerce.payment.domain.event.PaymentDeclinedEvent;
import com.ecommerce.payment.domain.exception.OrderNotPayableException;
import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentAttempt;
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
import com.ecommerce.shared.money.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Submits a payment attempt for a customer's own order (ADR-0005). Backs
 * {@code POST /api/v1/orders/{orderId}/payments}.
 *
 * <p><strong>Idempotent replay first</strong> (ADR-0005 item 2; V007's
 * {@code uq_payment_attempts_idempotency}): looks up any existing {@link Payment} for this order
 * and, within it, any {@link PaymentAttempt} already recorded under this exact
 * {@code Idempotency-Key}. If found, returns that recorded outcome directly — the gateway is never
 * called a second time for the same key.
 *
 * <p><strong>Fast-fail before the gateway</strong> (ADR-0005 §Decision item 2, §Compliance
 * checklist item 1): otherwise, reads the order via {@link OrderPort#getForPayment} and rejects
 * with {@link OrderNotPayableException} (422) if it is not currently payable — <em>before</em>
 * {@link PaymentGatewayPort} is ever invoked. This is a UX/cost optimization, not the correctness
 * guarantee; the guarantee is {@link OrderPort#markPaid}'s own translated
 * {@code Order.markPaid} state-transition guard.
 *
 * <p><strong>Amount is never client-supplied</strong> (ADR-0005 sanity check, security-critical):
 * the charge amount comes exclusively from {@code OrderView.grandTotal()} at first-attempt time,
 * snapshotted onto the {@link Payment} aggregate and reused for every subsequent retry — this
 * command carries only {@code orderId} and the {@code Idempotency-Key} header value, no amount
 * field.
 *
 * <p><strong>Declines/timeouts never touch order state</strong> (ADR-0005 §Compliance checklist
 * item 2): {@link OrderPort#markPaid} is called if and only if the gateway outcome is
 * {@code APPROVED}. On {@code DECLINED}/{@code TIMEOUT} the attempt is recorded and this method
 * returns normally (2xx) with that outcome — the {@link Payment} stays {@code PENDING} and the
 * order stays untouched, retryable within the checkout payment window.
 *
 * <p><strong>Atomicity</strong> (ADR-0005 §Compliance checklist item 5): the gateway call, the
 * {@link Payment} mutation, and (on approval) {@link OrderPort#markPaid} all execute inside this
 * one {@code @Transactional} method — default Spring {@code REQUIRED} propagation means
 * {@code MarkOrderPaidFromPaymentUseCase} (reached transitively through {@link OrderPort}) joins
 * this same physical DB transaction rather than opening a new one.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class SubmitPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final OrderPort orderPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SubmitPaymentUseCase(PaymentRepository paymentRepository,
                                OrderPort orderPort,
                                PaymentGatewayPort paymentGatewayPort,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.orderPort = Objects.requireNonNull(orderPort);
        this.paymentGatewayPort = Objects.requireNonNull(paymentGatewayPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public SubmitPaymentResult execute(SubmitPaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<Payment> existing = paymentRepository.findByOrderIdAndCustomerId(
                command.orderId(), command.customerId());

        if (existing.isPresent()) {
            Optional<PaymentAttempt> replay = existing.get().findAttemptByIdempotencyKey(command.idempotencyKey());
            if (replay.isPresent()) {
                return SubmitPaymentResult.of(existing.get(), replay.get());
            }
        }

        OrderView view = orderPort.getForPayment(command.orderId(), command.customerId());
        if (!view.payable()) {
            throw new OrderNotPayableException(
                    "Order " + command.orderId() + " is not currently payable");
        }

        Instant now = clock.instant();
        Payment payment;
        if (existing.isPresent()) {
            payment = existing.get();
        } else {
            payment = paymentRepository.create(
                    Payment.initiate(PaymentId.generate(), command.orderId(), command.customerId(),
                            view.grandTotal(), now));
        }

        GatewayOutcome gatewayOutcome = paymentGatewayPort.charge(
                new ChargeRequest(command.orderId(), payment.getAmount(), command.idempotencyKey()));

        payment.recordAttempt(command.idempotencyKey(), gatewayOutcome.outcome(), gatewayOutcome.declineReason(),
                gatewayOutcome.gatewayReference(), now);
        PaymentAttempt attempt = payment.getLatestAttempt();
        paymentRepository.appendAttempt(payment.getId(), attempt);

        if (gatewayOutcome.outcome() == PaymentOutcome.APPROVED) {
            payment.markCaptured(now);
            orderPort.markPaid(command.orderId()); // never called for DECLINED/TIMEOUT — ADR-0005 item 2/3
            Payment saved = paymentRepository.save(payment);
            eventPublisher.publishEvent(new PaymentCapturedEvent(
                    saved.getId(), saved.getOrderId(), saved.getCustomerId(), saved.getAmount(), now));
            return SubmitPaymentResult.of(saved, attempt);
        }

        if (gatewayOutcome.outcome() == PaymentOutcome.DECLINED) {
            eventPublisher.publishEvent(new PaymentDeclinedEvent(
                    payment.getId(), payment.getOrderId(), payment.getCustomerId(),
                    gatewayOutcome.declineReason(), now));
        }
        // TIMEOUT: no dedicated domain event exists in the catalog (domain-model.md §4 lists only
        // PaymentCaptured/Declined/Refunded) — the payment_attempts row itself is the audit trail.

        return SubmitPaymentResult.of(payment, attempt);
    }

    /**
     * @param orderId        the order being paid for
     * @param customerId     the caller, resolved from the JWT principal — never from the request
     *                       body (api-guidelines §7.4)
     * @param idempotencyKey the mandatory {@code Idempotency-Key} header value — the sole replay
     *                       correlator (ADR-0005 item 2); deliberately no amount field (ADR-0005
     *                       sanity check)
     */
    public record SubmitPaymentCommand(OrderId orderId, CustomerId customerId, String idempotencyKey) {

        public SubmitPaymentCommand {
            Objects.requireNonNull(orderId, "orderId must not be null");
            Objects.requireNonNull(customerId, "customerId must not be null");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey must not be null or blank");
            }
        }
    }

    /** Flat projection of the outcome the controller needs — never the full {@link Payment} aggregate. */
    public record SubmitPaymentResult(PaymentId paymentId, PaymentStatus status, PaymentOutcome outcome,
                                      DeclineReason declineReason, String gatewayReference, Money amount,
                                      Instant attemptedAt) {

        static SubmitPaymentResult of(Payment payment, PaymentAttempt attempt) {
            return new SubmitPaymentResult(payment.getId(), payment.getStatus(), attempt.outcome(),
                    attempt.declineReason(), attempt.gatewayReference(), payment.getAmount(),
                    attempt.attemptedAt());
        }
    }
}

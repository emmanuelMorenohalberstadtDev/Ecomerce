package com.ecommerce.payment.application.usecase;

import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.payment.domain.event.PaymentRefundedEvent;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.model.RefundReason;
import com.ecommerce.payment.domain.port.out.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Issues a full refund on a {@code CAPTURED} payment when its order is cancelled (ADR-0005 item
 * 4; domain-model.md §1 payment, rule 7) — invoked by
 * {@code RefundOnOrderCancelledListener}'s {@code @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p><strong>Two legitimate "nothing to do" cases, handled gracefully, not thrown</strong> (mirrors
 * {@code FailOrderFromCheckoutExpiryUseCase}'s "no order yet" reasoning):
 * <ul>
 *   <li>No {@link Payment} exists for the cancelled order at all — the customer cancelled before
 *       ever attempting payment (legal: {@code PLACED -> CANCELLED} does not require a payment
 *       attempt to have happened).</li>
 *   <li>A {@link Payment} exists but is not {@link PaymentStatus#CAPTURED} — still {@code PENDING}
 *       (every attempt so far was declined/timed out, or none was ever recorded) or already
 *       {@code REFUNDED} (a duplicate event delivery). Nothing to refund either way.</li>
 * </ul>
 *
 * <p><strong>No gateway call for the refund itself</strong> — ADR-0005 describes only the charge
 * direction as calling {@link com.ecommerce.payment.domain.port.out.PaymentGatewayPort}; refunds
 * v1 record {@code gatewayReference = null} (the column is nullable,
 * {@code V007__create_payment_schema.sql}), matching the absence of any refund-gateway requirement
 * in the ADR. Flagged as a judgment call in the handoff report — trivial to add a
 * {@code PaymentGatewayPort.refund(...)} method later if a real PSP requires one.
 *
 * <p><strong>Not {@code @PreAuthorize}-gated</strong>: this use case has no authenticated
 * principal to check — it runs from an {@code AFTER_COMMIT} event listener, mirroring
 * {@code FailOrderFromCheckoutExpiryUseCase}'s identical choice.
 *
 * <p>Transaction boundary is this use case class — a new, independent transaction from the one
 * that committed the {@code OrderCancelledEvent} (ADR-0005 item 4: "no atomicity requirement ties
 * refunds back to the order row").
 */
@Service
@Transactional
public class IssueRefundOnOrderCancelledUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueRefundOnOrderCancelledUseCase.class);

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public IssueRefundOnOrderCancelledUseCase(PaymentRepository paymentRepository,
                                              ApplicationEventPublisher eventPublisher,
                                              Clock clock) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void execute(OrderCancelledEvent event) {
        Optional<Payment> maybePayment = paymentRepository.findByOrderId(event.orderId());
        if (maybePayment.isEmpty()) {
            log.info("No payment exists for cancelled order {} — nothing to refund", event.orderId());
            return;
        }

        Payment payment = maybePayment.get();
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            log.info("Payment {} for cancelled order {} is not CAPTURED (status={}) — nothing to refund",
                    payment.getId(), event.orderId(), payment.getStatus());
            return;
        }

        Instant now = clock.instant();
        payment.markRefunded(RefundReason.ORDER_CANCELLED, null, now);
        paymentRepository.appendRefund(payment.getId(), payment.getLatestRefund());
        Payment saved = paymentRepository.save(payment);

        eventPublisher.publishEvent(new PaymentRefundedEvent(
                saved.getId(), saved.getOrderId(), saved.getCustomerId(), saved.getAmount(), now));
    }
}

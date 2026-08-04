package com.ecommerce.payment.application.listener;

import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.payment.application.usecase.IssueRefundOnOrderCancelledUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Subscribes to order's {@link OrderCancelledEvent} — payment's first real subscriber of this
 * event (ADR-0005 item 4; ADR-0004's own follow-up note: "when payment is implemented: it becomes
 * OrderCancelledEvent's first real subscriber (refund)") — and delegates to
 * {@link IssueRefundOnOrderCancelledUseCase}, which carries its own {@code @Transactional}
 * boundary (this listener is a thin {@code @Component}, not itself transactional, mirroring
 * {@code order.application.listener.PlaceOrderFromCheckoutListener}'s split between listener and
 * the work it triggers).
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}, matching the one existing
 * precedent ({@code cart.application.listener.CartMergeEventListener}) and ADR-0004's
 * confirmation (ADR-0005 §Compliance checklist): the event is published inside order's own
 * transaction ({@code Order.cancel()} + reservation release), and this listener must not race that
 * commit. Order's own transaction has already completed by the time a refund is even attempted —
 * deliberately <strong>not</strong> symmetric with the charge direction: order never waits for, or
 * knows, the refund's outcome (ADR-0005 §Decision item 4).
 *
 * <p><strong>Consistency story if the handler itself fails</strong> (skill {@code transactions}):
 * accepted-and-logged in v1 — no retry/reconciliation job exists yet, mirroring
 * {@code PlaceOrderFromCheckoutListener}'s identical accepted trade-off. A failure here leaves a
 * cancelled order with a captured payment never refunded; flagged in the handoff report for
 * test-engineer/a future reconciliation sweep.
 */
@Component
public class RefundOnOrderCancelledListener {

    private final IssueRefundOnOrderCancelledUseCase issueRefundOnOrderCancelledUseCase;

    public RefundOnOrderCancelledListener(IssueRefundOnOrderCancelledUseCase issueRefundOnOrderCancelledUseCase) {
        this.issueRefundOnOrderCancelledUseCase = Objects.requireNonNull(issueRefundOnOrderCancelledUseCase);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        issueRefundOnOrderCancelledUseCase.execute(event);
    }
}

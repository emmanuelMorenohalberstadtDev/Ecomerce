package com.ecommerce.order.application.listener;

import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.order.application.usecase.PlaceOrderFromCheckoutUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Subscribes to checkout's {@link CheckoutAwaitingPaymentEvent} — the order hand-off seam
 * (ADR-0003 §Decision item 4; ADR-0004) — and delegates to {@link PlaceOrderFromCheckoutUseCase},
 * which carries its own {@code @Transactional} boundary (this listener is a thin
 * {@code @Component}, not itself transactional, mirroring
 * {@code cart.application.listener.CartMergeEventListener}'s split between listener and the
 * work it triggers).
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}: the event is published inside
 * checkout's own transaction (the reservation + session-state change), and order placement must
 * not race that commit — if checkout's transaction rolled back, no order should ever be created
 * for a reservation that was never actually confirmed.
 *
 * <p><strong>Consistency story if the handler itself fails</strong> (skill {@code transactions}):
 * accepted-and-logged in v1 — no retry/reconciliation job exists yet. A failure here leaves a
 * committed, reserved checkout session with no corresponding order; the reservation's payment
 * window will still lapse and {@code FailOrderFromCheckoutExpiryUseCase} will find no order to
 * fail (tolerated, see that class's javadoc). Flagged in the handoff report for test-engineer/a
 * future reconciliation sweep.
 */
@Component
public class PlaceOrderFromCheckoutListener {

    private final PlaceOrderFromCheckoutUseCase placeOrderFromCheckoutUseCase;

    public PlaceOrderFromCheckoutListener(PlaceOrderFromCheckoutUseCase placeOrderFromCheckoutUseCase) {
        this.placeOrderFromCheckoutUseCase = Objects.requireNonNull(placeOrderFromCheckoutUseCase);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckoutAwaitingPayment(CheckoutAwaitingPaymentEvent event) {
        placeOrderFromCheckoutUseCase.execute(event);
    }
}

package com.ecommerce.order.application.listener;

import com.ecommerce.checkout.domain.event.CheckoutSessionExpiredEvent;
import com.ecommerce.order.application.usecase.FailOrderFromCheckoutExpiryUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Subscribes to checkout's {@link CheckoutSessionExpiredEvent} (ADR-0003 §Decision item 4;
 * ADR-0004) and delegates to {@link FailOrderFromCheckoutExpiryUseCase}, which carries its own
 * {@code @Transactional} boundary — mirrors {@link PlaceOrderFromCheckoutListener}'s split between
 * listener and the work it triggers.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}: the event is published inside
 * checkout's own expiry transaction (session -> EXPIRED, reservation released); this handler must
 * not race that commit.
 */
@Component
public class FailOrderFromCheckoutExpiryListener {

    private final FailOrderFromCheckoutExpiryUseCase failOrderFromCheckoutExpiryUseCase;

    public FailOrderFromCheckoutExpiryListener(FailOrderFromCheckoutExpiryUseCase failOrderFromCheckoutExpiryUseCase) {
        this.failOrderFromCheckoutExpiryUseCase = Objects.requireNonNull(failOrderFromCheckoutExpiryUseCase);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckoutSessionExpired(CheckoutSessionExpiredEvent event) {
        failOrderFromCheckoutExpiryUseCase.execute(event);
    }
}

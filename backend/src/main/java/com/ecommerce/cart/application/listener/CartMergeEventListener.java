package com.ecommerce.cart.application.listener;

import com.ecommerce.auth.domain.event.UserAuthenticatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Subscribes to {@link UserAuthenticatedEvent} — the documented consumer wiring
 * (domain-model.md §4: "cart (MergeCartsUseCase)") for cart's merge-on-login policy (rule 1).
 *
 * <p><strong>This listener does not perform the merge.</strong> {@link UserAuthenticatedEvent}
 * carries only {@code UserId} + {@code occurredAt} — no guest-token correlator — so there is no
 * way from here to know which guest cart, if any, belongs to the browser that just logged in.
 * The actual merge is performed synchronously and opportunistically by
 * {@code CartController} on the caller's next cart request (see
 * {@code MergeCartsUseCase} javadoc for the full design-fork rationale). This listener exists so
 * the documented event → consumer edge is wired and observable (audit/telemetry), and as the
 * natural home for a future richer trigger if the auth flow is ever changed to carry a
 * correlator.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} per the task's guidance: the
 * event is published inside {@code LoginUseCase}'s transaction, and any listener side effect
 * must not race that commit (testing-strategy.md scopes AFTER_COMMIT listeners as integration-test
 * territory).
 */
@Component
public class CartMergeEventListener {

    private static final Logger log = LoggerFactory.getLogger(CartMergeEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserAuthenticated(UserAuthenticatedEvent event) {
        log.info("UserAuthenticatedEvent received for userId={} — cart merge (if any guest cart "
                        + "applies) is performed opportunistically on the next cart request, not here "
                        + "(event carries no guest-token correlator); see CartMergeEventListener javadoc.",
                event.userId());
    }
}

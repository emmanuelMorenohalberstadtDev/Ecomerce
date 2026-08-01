package com.ecommerce.cart.application.listener;

import com.ecommerce.auth.domain.event.UserAuthenticatedEvent;
import com.ecommerce.shared.id.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Thin test for {@link CartMergeEventListener} — per its javadoc and the backend-lead handoff
 * report, this listener currently only logs (it cannot perform the actual merge; see
 * {@code MergeCartsUseCase} javadoc for the design-fork rationale). This test only confirms the
 * {@code @TransactionalEventListener(AFTER_COMMIT)}-annotated handler method runs without
 * throwing for a well-formed event — not over-testing a stub.
 */
@Tag("unit")
class CartMergeEventListenerTest {

    @Test
    void onUserAuthenticated_doesNotThrow_forWellFormedEvent() {
        CartMergeEventListener listener = new CartMergeEventListener();
        UserAuthenticatedEvent event = new UserAuthenticatedEvent(UserId.generate(), Instant.now());

        assertThatCode(() -> listener.onUserAuthenticated(event)).doesNotThrowAnyException();
    }
}

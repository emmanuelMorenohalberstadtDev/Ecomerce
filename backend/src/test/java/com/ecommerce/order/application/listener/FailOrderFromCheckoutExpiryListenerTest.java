package com.ecommerce.order.application.listener;

import com.ecommerce.checkout.domain.event.CheckoutSessionExpiredEvent;
import com.ecommerce.order.application.usecase.FailOrderFromCheckoutExpiryUseCase;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link FailOrderFromCheckoutExpiryListener} — a thin
 * {@code @TransactionalEventListener(AFTER_COMMIT)} wrapper. Confirms it delegates the event
 * straight through to {@link FailOrderFromCheckoutExpiryUseCase#execute}, mirroring
 * {@link PlaceOrderFromCheckoutListenerTest}'s pattern for the sibling listener.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FailOrderFromCheckoutExpiryListenerTest {

    @Mock
    private FailOrderFromCheckoutExpiryUseCase failOrderFromCheckoutExpiryUseCase;

    private FailOrderFromCheckoutExpiryListener listener;

    @BeforeEach
    void setUp() {
        listener = new FailOrderFromCheckoutExpiryListener(failOrderFromCheckoutExpiryUseCase);
    }

    @Test
    void onCheckoutSessionExpired_delegatesToUseCase() {
        CheckoutSessionExpiredEvent event = new CheckoutSessionExpiredEvent(CheckoutSessionId.generate(),
                CustomerId.generate(), ReservationId.generate(), Instant.parse("2026-07-31T10:00:00Z"));

        listener.onCheckoutSessionExpired(event);

        verify(failOrderFromCheckoutExpiryUseCase).execute(event);
    }
}

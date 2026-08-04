package com.ecommerce.payment.application.listener;

import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.payment.application.usecase.IssueRefundOnOrderCancelledUseCase;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
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
 * Unit test for {@link RefundOnOrderCancelledListener} — a thin
 * {@code @TransactionalEventListener(AFTER_COMMIT)} wrapper. Confirms it delegates the event
 * straight through to {@link IssueRefundOnOrderCancelledUseCase#execute}, mirroring
 * {@code order.application.listener.FailOrderFromCheckoutExpiryListenerTest}'s pattern for the
 * sibling listener.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RefundOnOrderCancelledListenerTest {

    @Mock
    private IssueRefundOnOrderCancelledUseCase issueRefundOnOrderCancelledUseCase;

    private RefundOnOrderCancelledListener listener;

    @BeforeEach
    void setUp() {
        listener = new RefundOnOrderCancelledListener(issueRefundOnOrderCancelledUseCase);
    }

    @Test
    void onOrderCancelled_delegatesToUseCase() {
        OrderCancelledEvent event = new OrderCancelledEvent(OrderId.generate(), CustomerId.generate(),
                ReservationId.generate(), Instant.parse("2026-08-02T10:00:00Z"));

        listener.onOrderCancelled(event);

        verify(issueRefundOnOrderCancelledUseCase).execute(event);
    }
}

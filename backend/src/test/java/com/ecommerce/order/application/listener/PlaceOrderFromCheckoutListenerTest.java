package com.ecommerce.order.application.listener;

import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.order.application.usecase.PlaceOrderFromCheckoutUseCase;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link PlaceOrderFromCheckoutListener} — a thin
 * {@code @TransactionalEventListener(AFTER_COMMIT)} wrapper. Confirms it delegates the event
 * straight through to {@link PlaceOrderFromCheckoutUseCase#execute}, mirroring
 * {@code cart.application.listener.CartMergeEventListenerTest}'s "thin listener" pattern but with
 * an actual delegation to verify, since this listener (unlike cart's stub) has real work to hand
 * off.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PlaceOrderFromCheckoutListenerTest {

    @Mock
    private PlaceOrderFromCheckoutUseCase placeOrderFromCheckoutUseCase;

    private PlaceOrderFromCheckoutListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlaceOrderFromCheckoutListener(placeOrderFromCheckoutUseCase);
    }

    @Test
    void onCheckoutAwaitingPayment_delegatesToUseCase() {
        Money unitPrice = new Money(new BigDecimal("10.00"), "USD");
        CheckoutAwaitingPaymentEvent.Line line =
                new CheckoutAwaitingPaymentEvent.Line(ProductId.generate(), Quantity.of(1), unitPrice, unitPrice);
        CheckoutAwaitingPaymentEvent event = new CheckoutAwaitingPaymentEvent(CheckoutSessionId.generate(),
                CustomerId.generate(), CartId.generate(), ReservationId.generate(), List.of(line),
                Money.zero("USD"), Money.zero("USD"), unitPrice, Instant.parse("2026-07-31T10:15:00Z"),
                Instant.parse("2026-07-31T10:00:00Z"));

        listener.onCheckoutAwaitingPayment(event);

        verify(placeOrderFromCheckoutUseCase).execute(event);
    }
}

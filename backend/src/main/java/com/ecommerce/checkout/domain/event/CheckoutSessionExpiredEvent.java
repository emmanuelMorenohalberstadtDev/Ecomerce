package com.ecommerce.checkout.domain.event;

import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;

/**
 * Published after {@code ExpirePaymentWindowUseCase} moves a {@code CheckoutSession} to
 * {@code EXPIRED} and releases its reservation (ADR-0003 §Decision item 4). No subscribers exist
 * yet in this change — {@code order} is an empty skeleton; once built it subscribes from its own
 * {@code order.application.listener} package (domain-model.md §4 {@code OrderFailedEvent} audit
 * note: "release already done synchronously by checkout").
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record CheckoutSessionExpiredEvent(CheckoutSessionId checkoutSessionId, CustomerId customerId,
                                          ReservationId reservationId, Instant occurredAt) {

    public CheckoutSessionExpiredEvent {
        if (checkoutSessionId == null) throw new IllegalArgumentException("checkoutSessionId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

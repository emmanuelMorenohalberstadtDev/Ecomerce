package com.ecommerce.inventory.domain.event;

import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.List;

/**
 * Published after a {@code StockReservation} is released and its lines restocked (checkout
 * failure/expiry, order cancellation — rule 7).
 *
 * <p>No subscribers in v1 (domain-model.md §4: "rules 5, 6 traceability") — kept for the audit
 * trail and future subscribers (e.g. a checkout-side listener that reacts to an expiry-driven
 * release it did not itself initiate). Payload carries the reservation id, checkout session,
 * restocked lines, and a timestamp — never the full aggregate.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record StockReleasedEvent(ReservationId reservationId, CheckoutSessionId checkoutSessionId,
                                 List<ReservedLine> lines, Instant occurredAt) {

    public StockReleasedEvent {
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (checkoutSessionId == null) throw new IllegalArgumentException("checkoutSessionId must not be null");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("lines must not be null or empty");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
        lines = List.copyOf(lines);
    }
}

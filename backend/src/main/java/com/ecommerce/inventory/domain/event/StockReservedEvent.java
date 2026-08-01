package com.ecommerce.inventory.domain.event;

import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ReservationId;

import java.time.Instant;
import java.util.List;

/**
 * Published after a {@code StockReservation} is created and persisted (all lines' atomic
 * decrements already succeeded).
 *
 * <p>No subscribers in v1 (domain-model.md §4: "rules 5, 6 traceability") — kept for the audit
 * trail and future subscribers. Payload carries the reservation id, the checkout session it was
 * created for, the reserved lines (value objects, not the aggregate itself), and a timestamp —
 * never the full {@code StockReservation} aggregate.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record StockReservedEvent(ReservationId reservationId, CheckoutSessionId checkoutSessionId,
                                 List<ReservedLine> lines, Instant occurredAt) {

    public StockReservedEvent {
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (checkoutSessionId == null) throw new IllegalArgumentException("checkoutSessionId must not be null");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("lines must not be null or empty");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
        lines = List.copyOf(lines);
    }
}

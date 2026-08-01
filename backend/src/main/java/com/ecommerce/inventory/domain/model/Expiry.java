package com.ecommerce.inventory.domain.model;

import java.time.Instant;

/**
 * Immutable value object wrapping a {@link StockReservation}'s optional payment-window deadline
 * (domain-model.md §2 {@code Expiry}; maps to the nullable {@code stock_reservations.expires_at}).
 *
 * <p>The 15-minute payment-window <em>duration</em> (rules 5, 8) is owned by the checkout
 * context, not here (domain-model.md §1: checkout owns "the 15-minute payment window") —
 * inventory only stores whatever deadline the caller supplies at reservation creation and knows
 * how to compare it against "now"; it never computes the window itself.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record Expiry(Instant value) {

    public static Expiry none() {
        return new Expiry(null);
    }

    public static Expiry at(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("Expiry instant must not be null; use Expiry.none() instead");
        }
        return new Expiry(value);
    }

    public boolean isPresent() {
        return value != null;
    }

    /** @return {@code true} if this expiry is set and strictly before {@code now}. */
    public boolean isExpiredAsOf(Instant now) {
        return value != null && value.isBefore(now);
    }
}

package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for a stock Reservation (inventory context).
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record ReservationId(UUID value) {

    public ReservationId {
        if (value == null) {
            throw new IllegalArgumentException("ReservationId value must not be null");
        }
    }

    public static ReservationId generate() {
        return new ReservationId(UuidV7Generator.generate());
    }

    public static ReservationId of(UUID value) {
        return new ReservationId(value);
    }

    public static ReservationId of(String value) {
        return new ReservationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

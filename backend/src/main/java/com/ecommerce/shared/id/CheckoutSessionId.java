package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for a CheckoutSession aggregate.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record CheckoutSessionId(UUID value) {

    public CheckoutSessionId {
        if (value == null) {
            throw new IllegalArgumentException("CheckoutSessionId value must not be null");
        }
    }

    public static CheckoutSessionId generate() {
        return new CheckoutSessionId(UuidV7Generator.generate());
    }

    public static CheckoutSessionId of(UUID value) {
        return new CheckoutSessionId(value);
    }

    public static CheckoutSessionId of(String value) {
        return new CheckoutSessionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

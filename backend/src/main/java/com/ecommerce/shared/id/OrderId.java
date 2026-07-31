package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the Order aggregate.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record OrderId(UUID value) {

    public OrderId {
        if (value == null) {
            throw new IllegalArgumentException("OrderId value must not be null");
        }
    }

    public static OrderId generate() {
        return new OrderId(UuidV7Generator.generate());
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    public static OrderId of(String value) {
        return new OrderId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

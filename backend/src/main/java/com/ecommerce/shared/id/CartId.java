package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the Cart aggregate.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record CartId(UUID value) {

    public CartId {
        if (value == null) {
            throw new IllegalArgumentException("CartId value must not be null");
        }
    }

    public static CartId generate() {
        return new CartId(UuidV7Generator.generate());
    }

    public static CartId of(UUID value) {
        return new CartId(value);
    }

    public static CartId of(String value) {
        return new CartId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

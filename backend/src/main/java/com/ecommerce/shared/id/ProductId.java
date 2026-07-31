package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the Product aggregate.
 *
 * <p>Opaque wrapper: callers work with {@code ProductId}, never with a raw {@code UUID}.
 * Generated application-side using UUIDv7 to preserve insert-order locality.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record ProductId(UUID value) {

    public ProductId {
        if (value == null) {
            throw new IllegalArgumentException("ProductId value must not be null");
        }
    }

    public static ProductId generate() {
        return new ProductId(UuidV7Generator.generate());
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }

    public static ProductId of(String value) {
        return new ProductId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

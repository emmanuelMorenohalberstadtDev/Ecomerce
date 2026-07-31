package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for a User in the auth bounded context.
 *
 * <p>Kept distinct from {@link CustomerId} so the type system prevents accidental
 * cross-context identity confusion. When a customer logs in, the auth context works
 * with {@code UserId}; the cart/order contexts work with {@code CustomerId}. The
 * application layer is responsible for the mapping between them at context boundaries.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId value must not be null");
        }
    }

    public static UserId generate() {
        return new UserId(UuidV7Generator.generate());
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

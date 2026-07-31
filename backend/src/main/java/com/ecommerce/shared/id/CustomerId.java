package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the Customer aggregate (commerce context).
 *
 * <p>See also {@link UserId} for the auth context's identity concept. Both wrap a UUID
 * generated with UUIDv7; they are kept separate so the compiler catches accidental
 * cross-context identity misuse.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record CustomerId(UUID value) {

    public CustomerId {
        if (value == null) {
            throw new IllegalArgumentException("CustomerId value must not be null");
        }
    }

    public static CustomerId generate() {
        return new CustomerId(UuidV7Generator.generate());
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the Promotion aggregate.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record PromotionId(UUID value) {

    public PromotionId {
        if (value == null) {
            throw new IllegalArgumentException("PromotionId value must not be null");
        }
    }

    public static PromotionId generate() {
        return new PromotionId(UuidV7Generator.generate());
    }

    public static PromotionId of(UUID value) {
        return new PromotionId(value);
    }

    public static PromotionId of(String value) {
        return new PromotionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

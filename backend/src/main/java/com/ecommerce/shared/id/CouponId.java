package com.ecommerce.shared.id;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the Coupon aggregate.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record CouponId(UUID value) {

    public CouponId {
        if (value == null) {
            throw new IllegalArgumentException("CouponId value must not be null");
        }
    }

    public static CouponId generate() {
        return new CouponId(UuidV7Generator.generate());
    }

    public static CouponId of(UUID value) {
        return new CouponId(value);
    }

    public static CouponId of(String value) {
        return new CouponId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

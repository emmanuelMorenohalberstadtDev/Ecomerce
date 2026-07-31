package com.ecommerce.catalog.domain.model;

import com.ecommerce.shared.uuid.UuidV7Generator;

import java.util.UUID;

/**
 * Typed identity for the {@link Category} aggregate.
 *
 * <p>Catalog-owned identity — deliberately NOT promoted to {@code com.ecommerce.shared.id}
 * alongside {@code ProductId}. Nothing outside the catalog bounded context references a bare
 * category id today (design note surfaced to software-architect: promote this to shared-kernel
 * if/when another context — e.g. promotions' {@code EligibilityRule} — needs to reference a
 * category by id; domain-model.md §2 already anticipates that in the promotions row but v1 does
 * not implement promotions).
 *
 * <p>Opaque wrapper: callers work with {@code CategoryId}, never with a raw {@code UUID}.
 * Generated application-side using UUIDv7 to preserve insert-order locality, exactly mirroring
 * {@code ProductId}'s convention.
 *
 * <p>No Spring, JPA, or Jackson imports — satisfies {@code domain_is_framework_free}.
 */
public record CategoryId(UUID value) {

    public CategoryId {
        if (value == null) {
            throw new IllegalArgumentException("CategoryId value must not be null");
        }
    }

    public static CategoryId generate() {
        return new CategoryId(UuidV7Generator.generate());
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    public static CategoryId of(String value) {
        return new CategoryId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.ecommerce.catalog.domain.event;

import com.ecommerce.shared.id.ProductId;

import java.time.Instant;

/**
 * Published after a {@code Product} is persisted with {@code status = RETIRED}.
 *
 * <p>No subscribers in v1 (domain-model.md §4): cart learns of unavailability by checking the
 * catalog port at read time rather than reacting to this event, and it is kept purely for
 * future subscribers / the audit trail. Payload carries only the id and timestamp — never a
 * full aggregate snapshot.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record ProductRetiredEvent(ProductId productId, Instant occurredAt) {

    public ProductRetiredEvent {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

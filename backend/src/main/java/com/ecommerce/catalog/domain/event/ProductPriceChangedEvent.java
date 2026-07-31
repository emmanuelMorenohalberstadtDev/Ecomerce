package com.ecommerce.catalog.domain.event;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.time.Instant;

/**
 * Published after a {@code Product}'s base price is changed and persisted.
 *
 * <p>No subscribers in v1 (domain-model.md §4): checkout recalculates prices from catalog at
 * the point of use regardless, so nothing needs to react synchronously to this event yet — it
 * exists for the audit trail and future subscribers. Payload carries the id, old and new price,
 * and timestamp — never a full aggregate snapshot.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record ProductPriceChangedEvent(ProductId productId, Money oldPrice, Money newPrice,
                                       Instant occurredAt) {

    public ProductPriceChangedEvent {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (oldPrice == null) throw new IllegalArgumentException("oldPrice must not be null");
        if (newPrice == null) throw new IllegalArgumentException("newPrice must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

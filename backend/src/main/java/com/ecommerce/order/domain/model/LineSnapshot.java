package com.ecommerce.order.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.util.Objects;

/**
 * One immutable purchased line, frozen at order-placement time (domain-model.md §2 order
 * {@code LineSnapshot (name, unit price, qty)}; rule 3: an order line is never re-priced, never
 * re-reads catalog). Maps 1:1 to one {@code order_items} row
 * ({@code V006__create_order_schema.sql}) — this record doubles as {@code order}'s
 * {@code OrderLine} (domain-model.md §2/§3): {@code productId} + {@code quantity} already live
 * here, so a separate wrapper would only duplicate fields for no behavioural gain.
 *
 * <p>{@code productName} may be a placeholder (e.g. {@code "Unavailable product " + productId}) if
 * the product was retired in the narrow window between checkout's reservation commit and order's
 * listener firing (ADR-0004 item 1) — the database stores whatever value was resolved at placement
 * time, never re-reads catalog afterward.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record LineSnapshot(ProductId productId, String productName, Money unitPrice, Quantity quantity) {

    public LineSnapshot {
        Objects.requireNonNull(productId, "LineSnapshot productId must not be null");
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("LineSnapshot productName must not be null or blank");
        }
        Objects.requireNonNull(unitPrice, "LineSnapshot unitPrice must not be null");
        Objects.requireNonNull(quantity, "LineSnapshot quantity must not be null");
    }

    /** Convenience: this line's contribution to {@code items_total} — {@code unitPrice * quantity}. */
    public Money lineTotal() {
        return unitPrice.multiply(quantity.value());
    }
}

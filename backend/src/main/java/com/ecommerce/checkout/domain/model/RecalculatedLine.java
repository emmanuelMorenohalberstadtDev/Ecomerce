package com.ecommerce.checkout.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.util.Objects;

/**
 * One priced line within a {@link RecalculatedTotal} — checkout's own value object, built by
 * {@code checkout.infrastructure.adapter.PriceQuoteAdapter} from pricing's
 * {@code PriceQuotePort.PriceQuote} (ADR-0003 §Decision item 2: checkout's domain/application
 * layers never see a pricing type directly).
 *
 * <p><strong>Transient only:</strong> {@code V005__create_checkout_schema.sql} has no per-line
 * child table for {@code checkout_sessions} — only aggregate totals columns. A
 * {@link RecalculatedLine} therefore exists only in memory, as part of the use case's return
 * value; it is never persisted (see {@code CheckoutSessionJpaEntity}'s totals-only mapping).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record RecalculatedLine(ProductId productId, Quantity quantity, Money unitPrice, Money lineTotal) {

    public RecalculatedLine {
        Objects.requireNonNull(productId, "RecalculatedLine productId must not be null");
        Objects.requireNonNull(quantity, "RecalculatedLine quantity must not be null");
        Objects.requireNonNull(unitPrice, "RecalculatedLine unitPrice must not be null");
        Objects.requireNonNull(lineTotal, "RecalculatedLine lineTotal must not be null");
    }
}

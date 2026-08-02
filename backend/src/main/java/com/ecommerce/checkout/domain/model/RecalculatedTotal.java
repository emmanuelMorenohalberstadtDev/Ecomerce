package com.ecommerce.checkout.domain.model;

import com.ecommerce.shared.money.Money;

import java.util.List;
import java.util.Objects;

/**
 * Checkout's own snapshot of a recalculated total for a {@link CheckoutSession} (ADR-0003
 * §Decision item 2) — built by {@code checkout.infrastructure.adapter.PriceQuoteAdapter} from
 * pricing's {@code PriceQuotePort.PriceQuote}. Checkout's domain/application layers never import a
 * pricing type directly.
 *
 * <p>{@code lines} is empty when this value object is reconstituted from persistence — the
 * per-line breakdown is transient only, matching {@code checkout_sessions} carrying totals columns
 * alone (no per-line child table — see {@code V005__create_checkout_schema.sql} and
 * {@link RecalculatedLine}'s javadoc). It is only ever non-empty immediately after a fresh
 * recalculation, for a use case's in-memory return value.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record RecalculatedTotal(List<RecalculatedLine> lines, Money subtotal, Money discountTotal,
                                 Money shippingFee, Money grandTotal) {

    public RecalculatedTotal {
        Objects.requireNonNull(lines, "RecalculatedTotal lines must not be null");
        Objects.requireNonNull(subtotal, "RecalculatedTotal subtotal must not be null");
        Objects.requireNonNull(discountTotal, "RecalculatedTotal discountTotal must not be null");
        Objects.requireNonNull(shippingFee, "RecalculatedTotal shippingFee must not be null");
        Objects.requireNonNull(grandTotal, "RecalculatedTotal grandTotal must not be null");
        lines = List.copyOf(lines);
    }
}

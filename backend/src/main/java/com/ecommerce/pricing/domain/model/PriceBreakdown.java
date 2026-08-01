package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.money.Money;

import java.util.List;
import java.util.Objects;

/**
 * The full computed price for a set of price lines: per-line detail ({@link PricedLine}) plus the
 * aggregate subtotal, total discount, shipping fee, and grand total ({@link EffectivePrice}).
 *
 * <p>Invariant maintained by {@link PriceCalculator}: {@code subtotal} equals the exact sum of
 * every line's {@code lineTotal} (no penny drift — {@link Money} rounds once per value, at the
 * point each value is produced, never mid-aggregation).
 */
public record PriceBreakdown(
        List<PricedLine> lines,
        Money subtotal,
        Money discountTotal,
        Money shippingFee,
        EffectivePrice grandTotal) {

    public PriceBreakdown {
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(subtotal, "subtotal must not be null");
        Objects.requireNonNull(discountTotal, "discountTotal must not be null");
        Objects.requireNonNull(shippingFee, "shippingFee must not be null");
        Objects.requireNonNull(grandTotal, "grandTotal must not be null");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        lines = List.copyOf(lines);
    }
}

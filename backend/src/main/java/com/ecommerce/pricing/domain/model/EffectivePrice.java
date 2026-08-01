package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.money.Money;

import java.util.Objects;

/**
 * The final, all-in price a customer would pay for a set of price lines: subtotal (after
 * item-level discounts) plus shipping. No tax line — catalog's base price is already treated as
 * tax-inclusive in v1 (no tax engine, a deliberate non-goal, not a gap).
 */
public record EffectivePrice(Money amount) {

    public EffectivePrice {
        Objects.requireNonNull(amount, "amount must not be null");
    }
}

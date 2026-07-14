package com.ecommerce.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Shared-kernel money value object (domain-model.md, pricing rules).
 * Construction never rounds silently; {@link #applyRate} is the single place
 * discount math rounds (HALF_EVEN), so line totals always sum exactly.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    private static final int SCALE = 4;

    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("amount and currency are required");
        }
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("scale above " + SCALE + ": " + amount);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("negative amount: " + amount);
        }
        amount = amount.setScale(SCALE);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("negative quantity: " + quantity);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money applyRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0) {
            throw new IllegalArgumentException("rate must be non-negative");
        }
        return new Money(amount.multiply(rate).setScale(SCALE, RoundingMode.HALF_EVEN), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}

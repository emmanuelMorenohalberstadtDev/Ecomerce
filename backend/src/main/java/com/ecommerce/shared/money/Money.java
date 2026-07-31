package com.ecommerce.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable value object representing a monetary amount.
 *
 * <p>Invariants enforced at construction time:
 * <ul>
 *   <li>{@code amount} is non-null, scaled to exactly 2 decimal places (centavos), and non-negative.
 *   <li>{@code currency} is a non-blank, 3-character ISO 4217 code (format check only; exhaustive
 *       validation is out of scope for v1).
 * </ul>
 *
 * <p>All arithmetic returns a new {@code Money} instance; the record is therefore safe to share
 * across threads.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public record Money(BigDecimal amount, String currency) {

    // ── Compact constructor: validate and normalise ──────────────────────────

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount must not be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Money currency must not be blank");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException(
                    "Money currency must be a 3-character ISO 4217 code, got: " + currency);
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Money amount must be non-negative, got: " + amount);
        }
        // Normalise to exactly 2 decimal places
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency.toUpperCase();
    }

    // ── Factories ────────────────────────────────────────────────────────────

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    // ── Arithmetic ───────────────────────────────────────────────────────────

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Money subtraction would produce a negative result: "
                            + this + " - " + other);
        }
        return new Money(result, this.currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException(
                    "Multiplier must be non-negative, got: " + quantity);
        }
        BigDecimal result = this.amount
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        return new Money(result, this.currency);
    }

    public Money negate() {
        // Negation is only valid on zero; a non-zero Money is always positive.
        // Exposed for completeness (e.g., discount line items represented as negatives
        // before application). The compact constructor would reject a negative amount,
        // so we bypass it only for zero.
        if (this.amount.compareTo(BigDecimal.ZERO) == 0) {
            return this;
        }
        throw new IllegalStateException(
                "Cannot negate a non-zero Money amount — Money is always non-negative");
    }

    // ── Comparison helpers ───────────────────────────────────────────────────

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    // ── Presentation ─────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on Money with different currencies: "
                            + this.currency + " vs " + other.currency);
        }
    }
}

---
name: pricing
description: Pricing domain rules — Money arithmetic, price composition order, rounding policy, currency handling, and server-side price authority.
---

# Pricing

## Purpose

Every price in the system is computed by one engine, in one defined order, with one rounding policy — a peso of drift between cart, checkout, and invoice is a defect, not a detail.

## When to Use

Implementing anything that computes or displays a price; designing the pricing engine; deciding rounding/currency questions; reviewing money code.

## Rules

1. **Money is a value object**: `Money(BigDecimal amount, Currency currency)` — construction validates scale/sign; arithmetic methods enforce same-currency; `double`/`float` never touch money anywhere (including tests and TypeScript — amounts travel as strings, see skill `rest-api`).
2. **One composition order, fixed and documented**:
   `base (unit list price) → item-level promotions → line total (unit_final × qty) → cart-level promotions → subtotal → shipping → taxes → grand total`.
   Every feature computes in this order; the order itself changes only by ADR + product-owner decision.
3. **Rounding policy is singular**: `HALF_EVEN` (or per-locale ADR), applied at **defined points only** — after unit-price discount, and at each aggregate boundary; never round mid-calculation. Line totals must sum exactly to the subtotal shown (penny-drift test is mandatory).
4. **The server is the only price authority**: clients send product ids and quantities — never prices, never discount amounts, never totals. Any request field carrying a client-computed amount is a design defect (and a security finding — see skill `secure-coding`).
5. **Prices are temporal**: catalog price changes never mutate placed orders (order snapshots the final computed prices — skill `order-lifecycle`) and carts reprice at checkout (skill `shopping-cart`). Price history is kept (effective-dated rows) if "price at time X" ever matters — decide early, per ADR.
6. **Taxes and shipping are pricing-engine steps**, not ad-hoc additions in the checkout controller — even if v1 is a flat rule, it lives in the engine with its ordering position.
7. Display formatting is a frontend/shared-component concern (one Price component, locale-aware — skill `ecommerce-design-system`); the API always ships raw amount + currency.

## Examples

```java
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {
    private static final int SCALE = 4;
    public Money {
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY); // constructing never rounds silently
    }
    public Money multiply(int qty) { return new Money(amount.multiply(BigDecimal.valueOf(qty)), currency); }
    public Money applyRate(BigDecimal rate) {  // the ONE place discount math rounds
        return new Money(amount.multiply(rate).setScale(SCALE, RoundingMode.HALF_EVEN), currency);
    }
    public Money add(Money o) { requireSame(o); return new Money(amount.add(o.amount), currency); }
}
```

```
Penny-drift acceptance case (unit tier, mandatory):
  Given 3 units at $33.3333 with a 10% item discount
  Then sum(line totals) == displayed subtotal exactly (no ±0.01)
```

## Best Practices

- The pricing engine is a pure domain service: `(cart lines, promotion context, destination) → PriceBreakdown` — deterministic, no I/O, trivially unit-testable to full branch coverage (see skill `jacoco`).
- Return the full breakdown to the frontend (base, each discount with its name, shipping, taxes, total) — transparency converts, and support tickets die.
- Property-style tests for invariants: total ≥ 0; total == sum of parts; discount never exceeds its base.
- Store amounts as `numeric(19,4)` with `char(3)` currency even single-currency v1 (see skill `postgresql`) — retrofitting currency is brutal.

## Common Mistakes

- `double` sneaking in via mappers, JSON parsing (`Double.parseDouble`), or test fixtures — grep for it in review.
- Rounding at every intermediate step (compounding drift) or never (4-decimal prices shown to users).
- Discount math duplicated in the frontend "for instant feedback" that disagrees with the server (display the server's breakdown; optimistic UI shows *pending*, not *computed*).
- Percentage-off implemented as subtraction of a pre-rounded amount in one place and multiplication in another.
- Shipping/tax bolted into the checkout use case bypassing the engine — two sources of total truth.

## References

- Fowler, *Money* pattern (PoEAA); `BigDecimal`/`RoundingMode` javadoc
- See skills `promotions`, `shopping-cart`, `order-lifecycle`, `rest-api`, `ddd`

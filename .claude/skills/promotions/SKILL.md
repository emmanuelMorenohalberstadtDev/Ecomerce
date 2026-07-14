---
name: promotions
description: Promotions and coupons domain — promotion types, eligibility rules, stacking policy, coupon lifecycle, and the promotion→pricing engine contract.
---

# Promotions

## Purpose

Promotions are rules, not price edits: modeled so marketing can add campaigns without code drift, stacking is deterministic, and no combination ever produces a negative total.

## When to Use

Designing/implementing promotions or coupons, deciding stacking questions, reviewing discount logic, writing promotion test plans.

## Rules

1. **Promotion = eligibility + benefit + constraints**, modeled explicitly:
   - *Eligibility*: scope (item/category/cart), conditions (min amount, min qty, customer segment, first purchase).
   - *Benefit*: percentage off, fixed amount off, buy-X-get-Y, free shipping.
   - *Constraints*: validity window, total redemption cap, per-customer cap, combinability class.
2. **Strategy per benefit type** (skill `design-patterns`): `DiscountPolicy` implementations composed by the pricing engine at the fixed composition points (item-level, then cart-level — order owned by skill `pricing`, never re-decided per promotion).
3. **Stacking is a policy table, not emergent behavior**: each promotion declares its combinability class (e.g., `STACKABLE`, `EXCLUSIVE`, `EXCLUSIVE_IN_CATEGORY`); conflicts resolve by a documented rule — default: **best single deal for the customer** among non-combinable sets. Product-owner decides the policy; the engine enforces it; tests pin the matrix.
4. **Coupons are a claim on a promotion**, separate concepts: coupon code → promotion, with its own lifecycle (`ISSUED → REDEEMED / EXPIRED / REVOKED`), atomic redemption-cap enforcement (same conditional-update pattern as skill `inventory` — caps are stock).
5. **Hard invariants the engine enforces regardless of configuration**: line price ≥ 0, cart total ≥ 0, a promotion never discounts more than its scope's base, free-shipping affects only the shipping component.
6. **Applied promotions are auditable**: the price breakdown names each applied promotion (id + label + amount); orders snapshot them (skill `order-lifecycle`) — "why was this $X?" must be answerable forever.
7. **Validation timing**: eligibility checked at application *and* re-verified at order placement (a coupon valid when entered may be exhausted by payment time → explicit, friendly problem detail, never silent price change).

## Examples

```
Stacking decision matrix (the artifact every promotions feature ships with):
Active: A = 20% off shoes (EXCLUSIVE_IN_CATEGORY), B = $500 off carts ≥ $5000 (STACKABLE),
        C = coupon 10% off shoes (EXCLUSIVE_IN_CATEGORY)

Cart: shoes $4000, shirt $2000
  A vs C on shoes → non-combinable → best deal: A ($800) beats C ($400) → apply A
  B: cart base after item discounts = $5200 ≥ $5000 → applies → −$500
  Result: item level −$800 (A), cart level −$500 (B); breakdown lists both; total $4700
```

```java
public interface DiscountPolicy {
    boolean isEligible(PricingContext ctx);
    Discount apply(PricingContext ctx);   // Discount carries promotionId, label, Money amount
    CombinabilityClass combinability();
}
```

## Best Practices

- Promotions are data (rows) interpreted by strategies — adding a normal campaign means an INSERT, not a deploy; a genuinely new *mechanic* means a new strategy class (Open/Closed, skill `solid`).
- Test the matrix, not just each promotion alone: pairwise combinations of active promotion classes are the mandatory test-plan category here.
- Expire by validity window in queries (`where now() between starts_at and ends_at`), disable by flag for kill-switch — both, not either.
- Log every rejected coupon attempt with the reason code (expired/exhausted/not-eligible) — marketing will ask, support will need it.

## Common Mistakes

- Percentage + fixed discounts applied in whichever order the code iterated (stacking order changes the total — the composition order is law).
- Coupon redemption count checked then incremented non-atomically (the inventory TOCTOU, again).
- Negative totals from stacked fixed-amount discounts (the invariant clamp missing).
- Encoding one campaign's specifics in `if` statements inside the engine ("Black Friday" hardcode) — that's data.
- Deleting expired promotions (orders reference them in breakdowns — expire, never delete).

## References

- See skills `pricing` (composition order, Money), `design-patterns` (Strategy/Chain), `inventory` (atomic caps), `shopping-cart`, `order-lifecycle`

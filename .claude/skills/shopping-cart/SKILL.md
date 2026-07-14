---
name: shopping-cart
description: Shopping cart domain rules — cart states, guest/authenticated merge, price snapshots, expiration, concurrency, and the cart→checkout boundary.
---

# Shopping Cart

## Purpose

Model the cart correctly: the domain rules, edge cases, and race conditions that every serious store must handle — decided once, implemented consistently.

## When to Use

Designing/implementing cart features, deciding merge/expiration/snapshot behavior, writing cart test plans.

## Rules

1. **Cart states**: `ACTIVE` → `CONVERTED` (became an order) | `EXPIRED` | `ABANDONED` (analytics distinction). One ACTIVE cart per identity, enforced by partial unique index (see skill `postgresql`).
2. **Cart lines snapshot display data, not authority**: a line stores `product_id`, `name`, `unit_price`, `image` *at add time* for display stability — but checkout **reprices from current catalog/pricing** and surfaces differences ("price changed from X to Y") before payment. The cart is a working document; the order is the contract.
3. **Guest carts are first-class**: identified by a server-issued cart token (httpOnly cookie), full functionality, TTL (e.g., 7 days, config `app.cart.guest-ttl`).
4. **Merge on login is a business policy, decided by product-owner and encoded once**: default = union of lines; same product → sum quantities capped by stock and per-line max; guest cart then marked merged. Never silently discard either cart.
5. **Cart mutations are use cases with optimistic locking** (`@Version`): concurrent add + checkout on the same cart must not lose updates; conflict → retry or 409 (see skills `jpa`, `transactions`).
6. **Stock at cart time is advisory only**: adding to cart reserves nothing (see skill `inventory`); the cart shows availability but the truth is checked at checkout. UI copy must not promise ("in your cart" ≠ "reserved").
7. **Quantity rules validated in the domain**: min 1, per-line max (config), per-cart line-count max — enforced by `Quantity`/`Cart` invariants, mirrored in UI.
8. Cart totals (subtotal, discounts, estimated shipping) are always computed server-side and returned by the API — the frontend renders, never calculates (see skill `rest-api` money rules).

## Examples

State + merge decision table (the artifact this skill expects to exist per feature):

```
Event                        | Guest cart      | User cart       | Result
-----------------------------|-----------------|-----------------|---------------------------
Login, both carts non-empty  | milk ×2, pan ×1 | milk ×1         | ACTIVE user cart: milk ×3, pan ×1; guest → merged
Login, guest empty           | —               | any             | user cart unchanged
Checkout with stale price    | line @ $100     | catalog @ $120  | reprice step: show delta, require confirm
Add beyond stock             | wants 5, stock 3| —               | line capped at 3 + problem detail `insufficient-stock`? No: cart allows 5, checkout enforces — per PO decision, recorded
```

(The last row is exactly the kind of rule product-owner must decide explicitly — this skill's job is to force the question.)

## Best Practices

- Expire carts with a scheduled job flipping status (auditable), not `DELETE` (analytics + "restore my cart" support).
- Return the full cart summary from every mutation endpoint — the UI's single source after each action (saves round trips, kills drift).
- Log cart→order conversion with the cart id on the order (funnel analytics comes free).
- Test the merge matrix and the concurrent-mutation case explicitly (integration tier — see skill `testing-strategy`).

## Common Mistakes

- Treating cart lines as authoritative prices at checkout (sell at a stale price or get gamed by long-lived carts).
- One cart table row per (user, session, device) accidentally — the uniqueness rule unenforced.
- Deleting guest carts on login instead of merging (users lose items = lost sales + support tickets).
- Reserving stock on add-to-cart "to be safe" (inventory locked by window-shoppers; see skill `inventory` for why not).
- Client-side cart (localStorage only) that evaporates across devices and can't be merged or analyzed.

## References

- Baymard Institute cart/checkout research (UX rationale)
- See skills `inventory`, `pricing`, `order-lifecycle`, `ddd`, `jpa`

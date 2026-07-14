---
name: inventory
description: Inventory domain rules — stock model, reservation at checkout, oversell prevention under concurrency, release/expiry, and stock visibility.
---

# Inventory

## Purpose

Never sell what you don't have, never block stock you could sell — correct concurrent stock accounting is the hardest invariant in the system, designed here once.

## When to Use

Designing stock models, implementing reservation/release, fixing oversell bugs, writing concurrency test plans.

## Rules

1. **Stock model per SKU**: `on_hand` (physical), `reserved` (held by pending checkouts/orders), **available = on_hand − reserved** (computed, never stored as a third mutable number to drift).
2. **Reservation happens at checkout start (or order placement — per ADR), never at add-to-cart**: carts don't hold stock (see skill `shopping-cart`). A reservation has a TTL (e.g., 15 min payment window) and an owning order/checkout id.
3. **Oversell prevention is a database-level guarantee**, chosen per ADR from:
   - *Atomic conditional update* (preferred default): `update stock set reserved = reserved + :q where sku = :sku and on_hand - reserved >= :q` — 0 rows updated = insufficient stock, no race window.
   - *Pessimistic lock* (`select … for update`) when multiple rows must move together (multi-line orders: lock in **consistent SKU order** to prevent deadlocks).
   - Optimistic `@Version` + retry acceptable only for low-contention SKUs.
   Blanket `SERIALIZABLE` is not the answer (retry storms).
4. **Reservations are explicit rows**, not just a counter bump: (sku, order_id, qty, expires_at, status) — auditable, individually releasable, reconcilable.
5. **Release paths all lead back**: payment failed, checkout abandoned (TTL sweep), order cancelled → release reservation; payment confirmed → reservation converts (`on_hand −= q`, `reserved −= q`) atomically with order transition.
6. **Reconciliation job exists from day one**: `sum(active reservations) == reserved` per SKU, expired reservations swept — eventual-consistency safety net, alerting on drift.
7. **Multi-line orders reserve all-or-nothing** in one transaction: partial reservation = release everything + report which lines failed (problem detail lists the SKUs).
8. Stock display in UI: ranges/thresholds ("Only 3 left", "In stock"), not exact counts — exactness invites scraping and looks broken under concurrency (config thresholds, `stock-low` token from the design system).

## Examples

```java
// atomic conditional reserve — the no-race workhorse
@Modifying
@Query("""
    update StockEntity s
       set s.reserved = s.reserved + :qty
     where s.sku = :sku and s.onHand - s.reserved >= :qty
    """)
int tryReserve(String sku, int qty);   // 0 = insufficient → InsufficientStockException(sku, qty, available)
```

```
Concurrency acceptance case (integration tier, mandatory):
  Given SKU with on_hand=1, reserved=0
  When 2 checkouts reserve 1 unit in parallel
  Then exactly one succeeds, one gets insufficient-stock, and reserved == 1
```

## Best Practices

- Every stock movement writes a ledger row (sku, delta, reason, reference) — the counters are a cache of the ledger's sum; disputes and bugs are debuggable.
- Test the TTL sweep + payment race: payment succeeds at minute 15:01 against an expired reservation → defined behavior (re-reserve or fail gracefully), decided by product-owner, tested.
- Keep reservation TTL, low-stock thresholds, and per-line max in typed config (`@ConfigurationProperties`), not constants.
- Deadlock discipline: any multi-SKU locking path sorts by SKU — document it where the code locks.

## Common Mistakes

- `stock = stock - qty` after a `select` check (the classic TOCTOU oversell) — check-and-act must be one atomic statement or hold a lock.
- Reserving at add-to-cart (window-shoppers exhaust the catalog; flash sales die).
- Counters without a ledger — the first drift incident is undiagnosable.
- Releasing reservations only on the happy cancel path (abandoned checkouts leak `reserved` forever — the TTL sweep is not optional).
- Showing `available` computed in the frontend from stale numbers, then 409ing at checkout without explaining which line failed.

## References

- PostgreSQL explicit locking docs; *Designing Data-Intensive Applications* ch. 7 (races)
- See skills `transactions`, `jpa`, `order-lifecycle`, `shopping-cart`, `testcontainers`

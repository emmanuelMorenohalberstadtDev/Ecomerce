---
name: order-lifecycle
description: The order state machine — states and legal transitions, immutable order snapshots, idempotent placement, payment coordination, and lifecycle events.
---

# Order Lifecycle

## Purpose

An order is a contract: an immutable snapshot moving through an explicit state machine where illegal transitions are impossible and every transition is an auditable event.

## When to Use

Designing/implementing order placement, payment/shipping/cancellation flows, state-machine reviews, order test plans.

## Rules

1. **The state machine (v1)** — transitions exist ONLY as domain methods; there is no `setStatus`:
   ```
   PLACED ──pay()──▶ PAID ──ship()──▶ SHIPPED ──deliver()──▶ DELIVERED
     │                 │
     └──cancel()──▶ CANCELLED ◀──cancel()──┘   (per policy: PAID cancellable until shipped)
   PLACED ──paymentFailed()──▶ PAYMENT_FAILED ──retry→ (new payment attempt, order stays)
   ```
   Cancellation policy per state (who may cancel, until when, refund implications) is a product-owner decision table, encoded in the domain, tested per cell.
2. **Orders snapshot everything at placement**: product names, unit prices, the full price breakdown (applied promotions by name — skill `promotions`), shipping address, currency. Later catalog/price/address changes never touch a placed order. The order references `product_id` for analytics, but *displays from its own rows*.
3. **Placement is idempotent**: `Idempotency-Key` required (skill `rest-api`); the key + resulting order id persist in the placement transaction — a network-retry double-click yields the same order, never two.
4. **Placement transaction does exactly**: validate + reprice cart → reserve stock (skill `inventory`) → create order PLACED with snapshot → convert cart → commit. Payment is a **separate step** against the placed order — never an external call inside the placement transaction (skill `transactions`).
5. **Transitions emit domain events** (`OrderPlacedEvent`, `OrderPaidEvent`, `OrderCancelledEvent`…) handled AFTER_COMMIT: stock conversion/release, email, analytics react to events — the order aggregate calls no other context directly.
6. **Every transition is recorded**: `order_status_history` (order_id, from, to, at, actor, reason) — support's first question is "what happened to this order"; the answer is a query, not archaeology.
7. **Concurrent transitions resolved by locking + state guard**: cancel racing ship → one wins by version/lock, the loser gets a state-conflict problem detail (`order-not-cancellable`), not a silent overwrite.
8. Timeouts are explicit lifecycle rules: PLACED orders whose payment window expires (reservation TTL, skill `inventory`) → auto-transition via sweep job (`paymentExpired()` → CANCELLED + release), same machine, same history, same events.

## Examples

```java
public class Order {
    private OrderStatus status;
    @Version private long version;

    public void cancel(CancellationReason reason, Clock clock) {
        if (!status.isCancellableBy(reason.actor()))
            throw new IllegalOrderTransitionException(id, status, "cancel");
        transition(OrderStatus.CANCELLED, reason, clock);   // records history + registers event
    }
    // pay(), ship(), deliver() follow the same shape — guard, transition, event
}
```

```
Mandatory test-plan rows for any lifecycle feature:
  - every legal transition (happy)                      → unit
  - every ILLEGAL transition throws (full matrix)       → unit (parameterized over states)
  - cancel vs ship race: one 200, one 409               → integration
  - duplicate Idempotency-Key returns original order    → API
  - payment-window sweep cancels + releases stock       → integration
```

## Best Practices

- Sealed `OrderStatus` (or State pattern per skill `design-patterns`) so the transition matrix lives in one file the whole team can read — the diagram in the docs is generated from/checked against it.
- Refunds, returns, partial shipments are *future states* — leave the machine closed until product-owner specifies them (YAGNI), but keep history/events shaped so extensions don't rewrite the past.
- The frontend renders status from a single source (`status` + allowed actions list served by the API — `canCancel: true`) so UI buttons never guess the policy.
- Reconcile orders stuck in PLACED/PAYMENT_FAILED beyond thresholds — dashboards + the sweep job, from day one.

## Common Mistakes

- `order.setStatus(SHIPPED)` scattered in services — the machine exists only if bypassing it is impossible.
- Joining orders to products for display (order shows today's name/price for a 2-year-old purchase).
- Charging the customer inside the placement transaction (payment gateway latency/failure corrupts placement; and a commit failure after charge = charged, no order).
- Cancelling without releasing reservations, or double-releasing on cancel-after-expiry (transitions own their side effects via events, exactly once).
- Status history as log lines instead of rows (unqueryable, rotated away).

## References

- See skills `ddd` (aggregate design), `design-patterns` (State), `transactions` (events AFTER_COMMIT), `inventory` (reserve/release), `pricing`/`promotions` (snapshot content), `rest-api` (idempotency)

---
name: transactions
description: Transaction management in Spring — boundaries at use cases, propagation, isolation, readOnly, rollback rules, and pitfalls like self-invocation.
---

# Transactions

## Purpose

Make every state change atomic and consistent with explicit, minimal transaction boundaries — one use case, one transaction, one aggregate.

## When to Use

Writing use cases, deciding propagation/isolation, mixing transactional work with external calls, debugging "why didn't it roll back".

## Rules

1. **Boundary = the use case** (application layer). Controllers never open transactions; repositories never own them beyond their single statement.
2. **`readOnly = true`** on every query-only use case — enables driver/Hibernate optimizations and documents intent.
3. **Default propagation (`REQUIRED`)** unless a documented reason: `REQUIRES_NEW` only for must-commit-independently records (audit, idempotency-key storage); never as a "fix" for rollback confusion.
4. **Isolation**: PostgreSQL default `READ_COMMITTED` + optimistic locking covers most cases. Stock decrement correctness uses explicit locking or atomic UPDATE (see skill `inventory`), not blanket `SERIALIZABLE`.
5. **Rollback rules**: runtime exceptions roll back, checked don't — this project throws runtime domain exceptions, so defaults work; never `@Transactional(rollbackFor = Exception.class)` scattered as cargo cult.
6. **No external I/O inside transactions**: payment gateway calls, emails, HTTP — happen before (validate/reserve), or after commit (events via `@TransactionalEventListener(phase = AFTER_COMMIT)`). A transaction held through a 3-second HTTP call is a pool-killer.
7. **Self-invocation doesn't proxy**: `this.otherTransactionalMethod()` ignores the annotation. Restructure into separate beans instead of injecting self.
8. One transaction modifies one aggregate (DDD rule); cross-aggregate consistency via after-commit events.

## Examples

```java
@Transactional
public class PlaceOrderUseCase {
    public OrderResult execute(PlaceOrderCommand cmd) {
        var cart = carts.findActiveBy(cmd.customerId()).orElseThrow(...);
        var order = Order.place(cart, pricing.priceFor(cart));   // domain
        orders.save(order);
        events.publish(new OrderPlacedEvent(order.id()));         // handled AFTER_COMMIT
        return OrderResult.from(order);
        // payment charge happens in a separate step/use case — not inside this tx
    }
}

@Component class StockReservationHandler {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    void on(OrderPlacedEvent e) { /* new transaction reserves stock */ }
}
```

## Best Practices

- Keep transactions short: load → decide → save. Push mapping/serialization outside.
- Name the consistency story in the PR when using events: what happens if the after-commit handler fails (retry? reconciliation job?) — eventual consistency is a design, not an accident.
- Test rollback behavior explicitly (test-engineer): throw after save, assert nothing persisted.
- `@Transactional` on the class for use cases (one public method), on methods elsewhere — pick per conventions, stay consistent.

## Common Mistakes

- `@Transactional` on controller or on every repository method (nested no-ops hiding the real boundary).
- Catching the exception inside the transactional method and returning normally — Spring sees success, commits the partial work.
- Publishing events with plain `ApplicationEventPublisher` and doing external I/O in a synchronous listener *inside* the transaction.
- `REQUIRES_NEW` inside a loop creating hundreds of tiny transactions.
- Long-running "batch" logic in one transaction — chunk it.

## References

- Spring Framework transaction docs; PostgreSQL isolation docs
- See skills `jpa`, `inventory`, `order-lifecycle`, `spring-boot`

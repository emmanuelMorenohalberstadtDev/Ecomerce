---
name: design-patterns
description: The design patterns that earn their keep in an ecommerce backend/frontend — when each applies, and which to avoid.
---

# Design Patterns

## Purpose

Shared vocabulary and proven structures for recurring problems — applied only where the problem actually recurs.

## When to Use

When a design problem matches a known pattern's forces; when naming a structure in reviews or ADRs.

## Rules

1. Name the problem before the pattern: "promotions vary and stack" → Strategy/Chain; never "let's use Strategy somewhere".
2. Patterns for this project, mapped to real forces:
   - **Strategy** — discount policies, payment providers, shipping calculators.
   - **Factory / static factory methods** — creating aggregates with invariants (`Order.place(...)`).
   - **Builder** — test data construction (see skill `testcontainers`/`junit`); complex immutable objects.
   - **Adapter** — every infrastructure implementation of a port (JPA repos, gateway clients).
   - **Decorator** — cross-cutting on ports (logging/metrics around `PaymentGatewayPort`).
   - **Observer / domain events** — cross-context reactions (`OrderPlacedEvent` → inventory, email).
   - **State** — order lifecycle transitions (see skill `order-lifecycle`).
   - **Chain of Responsibility** — promotion/pricing pipelines with ordered rules.
   - **Facade** — a use case *is* a facade over domain operations; don't add another layer.
3. Java 21 idioms count as patterns: sealed hierarchies + pattern matching replace Visitor for closed sets; records replace hand-built Value Objects.
4. Every pattern introduction states its force in the PR description; reviewer checks the force is real.

## Examples

```java
// State: transitions live in one place, invalid ones are impossible
public sealed interface OrderState permits Placed, Paid, Shipped, Delivered, Cancelled {
    default OrderState pay() { throw new IllegalTransitionException(this, "pay"); }
}
record Placed() implements OrderState {
    public OrderState pay() { return new Paid(); }
    public OrderState cancel() { return new Cancelled(); }
}
```

```java
// Decorator on a port — retry/metrics without touching the adapter
class MeteredPaymentGateway implements PaymentGatewayPort {
    private final PaymentGatewayPort delegate;
    public PaymentResult charge(ChargeRequest r) { /* time it, delegate, record */ }
}
```

## Best Practices

- Prefer the language feature over the classic pattern when equivalent (sealed+switch > Visitor; records > Builder for ≤ 4 fields).
- Keep pattern names in class names only when they clarify (`JpaCartRepository` adapter needs no "Adapter" suffix; `DiscountPolicy` strategy needs no "Strategy").
- One pattern per problem; stacking three patterns on a simple need is architecture astronautics.

## Common Mistakes

- Singleton anything — Spring's container manages lifecycle; hand-rolled singletons hide dependencies and break tests.
- Abstract factories of factories for a domain with one variant.
- Observer for same-context logic that should be a direct, explicit call (events hide control flow — spend that cost only across contexts).
- Template Method deep hierarchies — prefer Strategy composition.

## References

- GoF, *Design Patterns*; Joshua Bloch, *Effective Java* (3rd ed.) items 1–5, 17
- See skills `solid`, `ddd`, `order-lifecycle`

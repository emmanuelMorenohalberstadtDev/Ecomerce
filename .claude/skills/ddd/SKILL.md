---
name: ddd
description: Domain-Driven Design tactics for the ecommerce — entities, value objects, aggregates, domain events, bounded contexts, and ubiquitous language.
---

# DDD

## Purpose

Model the business faithfully in code so business rules live in one obvious place and the team (and agents) share one vocabulary.

## When to Use

Designing domain models, deciding aggregate boundaries, naming things, deciding what crosses context boundaries.

## Rules

1. **Ubiquitous language**: code uses the business's words — `Cart`, `place order`, `reserve stock` — identically in stories, code, and docs. One glossary, no synonyms (`Basket` vs `Cart` = bug).
2. **Bounded contexts** for this project: `catalog`, `cart`, `checkout`, `order`, `payment`, `inventory`, `pricing`, `promotions`, `auth/user`. A concept may differ across contexts (catalog's `Product` is rich; cart's is a `ProductSnapshot`).
3. **Entities** have identity and lifecycle (`Order`, `Cart`). **Value objects** are immutable, compared by value (`Money`, `Address`, `Quantity`, `Email`) — model as records with validating factories.
4. **Aggregates**: a consistency boundary with one root. Outside code references other aggregates by ID only. `Order` owns its `OrderLine`s; it references `ProductId`, never `Product`.
5. One transaction modifies one aggregate. Cross-aggregate effects flow through **domain events** (`OrderPlacedEvent` → inventory reserves).
6. Keep aggregates small: if concurrent users routinely edit the same aggregate, the boundary is too big.
7. **Repositories** exist per aggregate root only — no `OrderLineRepository`.

## Examples

```java
// Value object: invariant enforced at construction, immutable forever
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.scale() > 4) throw new IllegalArgumentException("scale");
        if (amount.signum() < 0) throw new IllegalArgumentException("negative");
    }
    public Money add(Money other) { requireSameCurrency(other); /* ... */ }
}

// Aggregate root guards its invariants; lines are internal
public class Order {
    private final List<OrderLine> lines; // no getter exposing mutable list
    public static Order place(CustomerId c, List<OrderLine> lines, Money total) {
        if (lines.isEmpty()) throw new EmptyOrderException();
        // ... returns Order in PLACED state, records OrderPlacedEvent
    }
}
```

## Best Practices

- Snapshot pattern at boundaries: cart stores product name/price *at add time* — catalog price changes must not mutate carts.
- Make illegal states unrepresentable: status transitions via methods (`order.cancel()`), not status setters.
- Domain events named in past tense — they are facts, not commands.
- Start with the context map (align with software-architect) before modeling inside any context.

## Common Mistakes

- One giant `Product` shared by all contexts — coupling every context to catalog's shape.
- Aggregates referencing other aggregates by object (loads half the database, breaks the consistency boundary).
- Anemic entities + "domain services" holding all logic — services are for logic spanning aggregates, not a default home.
- Using DDD ceremony for contexts with no rules (a `Country` reference table needs no aggregate design).

## References

- Eric Evans, *Domain-Driven Design*; Vaughn Vernon, *Implementing DDD* (aggregate rules)
- See skills `clean-architecture`, `order-lifecycle`, `inventory`, `pricing`

---
name: solid
description: The five SOLID principles applied to Java 21 and Angular 21 code — concrete detection rules and refactors for each principle.
---

# SOLID

## Purpose

Keep classes and modules easy to change: each principle prevents a specific kind of rigidity or fragility.

## When to Use

Writing or reviewing any class/component; deciding whether to split, extract, or abstract.

## Rules

1. **S — Single Responsibility**: one reason to change. Detection: a class name needing "And", or fields used by disjoint method groups. A use case handling validation + persistence + email = three reasons.
2. **O — Open/Closed**: extend behavior by adding code, not editing stable code. Detection: a growing `switch` on a type/status enum in multiple places → replace with polymorphism or a strategy map.
3. **L — Liskov Substitution**: subtypes honor the base contract — no strengthened preconditions, weakened postconditions, or `UnsupportedOperationException` overrides. Prefer composition when a subtype can't fully honor the contract.
4. **I — Interface Segregation**: no client forced to depend on methods it doesn't use. Detection: implementations with empty methods; fat "Repository" interfaces where a use case needs only `findById`.
5. **D — Dependency Inversion**: high-level policy depends on abstractions it owns. The domain declares `PaymentGatewayPort`; infrastructure implements it — never the domain importing an SDK.

## Examples

```java
// O + D: pricing strategies instead of a switch edited per promotion type
public interface DiscountPolicy { Money apply(Money base, CartContext ctx); }
// Adding "3x2" = new class + registration. Zero edits to existing policies.
```

```typescript
// S in Angular: container handles state, presentational component only renders
@Component({ selector: 'app-cart-item', changeDetection: OnPush })
export class CartItemComponent {
  item = input.required<CartItem>();
  quantityChange = output<number>();  // no services injected here
}
```

## Best Practices

- Apply at the seam where change is *expected* (pricing, promotions, payment providers) — SOLID follows the business's axes of change.
- In Java 21, sealed interfaces + records give O and L cheaply for closed sets (order events, payment results).
- Constructor injection everywhere makes D auditable: the constructor lists the true dependencies.

## Common Mistakes

- Interface-for-everything: an interface with one implementation and no test/extension need is noise, not D (YAGNI).
- Confusing S with "tiny classes" — cohesion is the test, not line count.
- "Fixing" L violations with `instanceof` checks in callers instead of redesigning the hierarchy.
- Strategy pattern for a set that has never changed and won't (KISS beats speculative O).

## References

- Robert C. Martin, *Agile Software Development: Principles, Patterns, and Practices*
- See skills `design-patterns`, `clean-architecture`

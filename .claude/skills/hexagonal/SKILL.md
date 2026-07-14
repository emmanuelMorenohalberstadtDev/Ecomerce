---
name: hexagonal
description: Ports & Adapters applied to Spring Boot — defining driving/driven ports, writing adapters, and testing the hexagon in isolation.
---

# Hexagonal Architecture (Ports & Adapters)

## Purpose

Make the application core (domain + use cases) talk to the outside world only through interfaces it owns, so any adapter — REST, database, payment provider, test double — is swappable.

## When to Use

Defining how a use case reaches the database or an external service; integrating any third-party system; structuring tests around the core.

## Rules

1. **Driving (inbound) ports**: the use case's public interface, called by controllers/schedulers. In practice the use case class itself is the port; extract an interface only when a second driver or test need exists.
2. **Driven (outbound) ports**: interfaces declared in domain/application, named after the *need* — `CartRepository`, `PaymentGatewayPort`, `EmailSenderPort`, `StockReservationPort`.
3. **Adapters** live in infrastructure and implement ports: `JpaCartRepository`, `StripePaymentGateway` (name by technology + port).
4. Port signatures use domain types only (`Money`, `OrderId`) — never JPA entities, HTTP DTOs, or vendor SDK types. Vendor types stop at the adapter.
5. The core compiles without infrastructure on the classpath (enforce with module/package tests).
6. Configuration wires adapters to ports in one place (`@Configuration` classes in infrastructure).

## Examples

```java
// application port — vendor-free signature
public interface PaymentGatewayPort {
    PaymentResult charge(OrderId orderId, Money amount, PaymentMethodToken token);
}

// infrastructure adapter — vendor types stay inside
class StripePaymentGateway implements PaymentGatewayPort {
    public PaymentResult charge(OrderId id, Money amount, PaymentMethodToken t) {
        try {
            PaymentIntent pi = /* stripe SDK call */;
            return PaymentResult.approved(new PaymentReference(pi.getId()));
        } catch (CardException e) {
            return PaymentResult.declined(DeclineReason.from(e)); // translated, not leaked
        }
    }
}

// test: the whole hexagon with fakes — no Spring, no network
var useCase = new PlaceOrderUseCase(new InMemoryOrderRepository(), new AlwaysApprovesGateway());
```

## Best Practices

- One port per need, not per vendor: `NotificationPort`, even if email is the only channel today — but only once a real second driver exists or testing demands it (YAGNI applies to ports too).
- Adapters translate errors: vendor exceptions become domain results/exceptions at the boundary.
- In-memory fakes of driven ports beat mocks for use-case tests — they verify behavior, not call sequences.

## Common Mistakes

- Ports that mirror a vendor API method-by-method — that's the adapter's interface leaked inward.
- Skipping the port for "just this one query" and injecting `EntityManager` into a use case.
- Interface + Impl pairs for classes with no boundary role (`CartServiceImpl`) — hexagonal is about the edges, not ceremony everywhere.
- Two ports for one need because two features each declared their own — consolidate via architect.

## References

- Alistair Cockburn, *Hexagonal Architecture*; Tom Hombergs, *Get Your Hands Dirty on Clean Architecture*
- See skills `clean-architecture`, `ddd`, `mockito` (when to fake vs mock)

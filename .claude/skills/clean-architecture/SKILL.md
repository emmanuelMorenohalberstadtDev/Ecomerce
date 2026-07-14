---
name: clean-architecture
description: Layered architecture with inward-pointing dependencies for the Spring Boot backend — layer responsibilities, the dependency rule, and how to keep the domain framework-free.
---

# Clean Architecture

## Purpose

Structure each bounded context so business rules are independent of frameworks, UI, and the database — testable in isolation and cheap to change.

## When to Use

Every backend feature. Load this skill when creating packages, deciding where a class goes, or reviewing for layer violations.

## Rules

1. **Dependency rule**: source dependencies point inward only. `presentation → application → domain` and `infrastructure → application/domain`. Domain depends on nothing.
2. **Domain layer** (`domain/`): entities, value objects, domain services, domain events, repository *interfaces*. Zero imports of Spring, JPA, Jackson, or any framework.
3. **Application layer** (`application/`): one class per use case, ports (interfaces) for outbound needs, application-level DTOs. May use framework annotations only for transaction demarcation.
4. **Infrastructure layer** (`infrastructure/`): JPA entities/adapters, external API clients, configuration. Implements domain/application interfaces.
5. **Presentation layer** (`presentation/`): controllers, request/response records, mappers to/from application DTOs. No business logic — translation only.
6. Crossing layers skips nothing: controller → use case → domain. Controller → repository is a violation.
7. Inner layers never know DTO shapes of outer layers; mapping happens at each boundary.

## Examples

```java
// domain/ — pure Java, protects its invariant
public class Cart {
    public void addItem(ProductId id, Quantity qty, Money unitPrice) {
        if (status != CartStatus.ACTIVE) throw new CartNotActiveException(this.id);
        // ...
    }
}

// application/ — orchestrates, owns the transaction boundary
@Transactional
public class AddItemToCartUseCase {
    private final CartRepository carts;       // domain interface
    private final ProductCatalogPort catalog; // application port
    public CartSummary execute(AddItemCommand cmd) { /* load, call domain, save */ }
}

// infrastructure/ — implements the interface with JPA
class JpaCartRepository implements CartRepository { /* maps JPA entity <-> domain */ }
```

Violation to catch in review: `import jakarta.persistence.*` anywhere under `domain/`.

## Best Practices

- Separate JPA entities from domain entities when invariants matter (cart, order, inventory); simple reference data may share a class only by explicit ADR.
- Enforce mechanically: ArchUnit tests asserting package dependencies.
- Keep use cases small and named after the business action (`PlaceOrderUseCase`, not `OrderService.process`).
- Ports are named by need (`PaymentGatewayPort`), not by vendor.

## Common Mistakes

- "Domain" objects that are annotated JPA entities with public setters — that's the persistence model wearing a costume.
- Use cases returning JPA entities to controllers (leaks persistence outward).
- A shared `common/` package that quietly becomes an everything-depends-on-it layer.
- Creating all four layers for trivial CRUD reference data — apply proportionality, record the exception.

## References

- Robert C. Martin, *Clean Architecture* (2017)
- See skills `hexagonal` (ports/adapters detail), `ddd` (what goes in domain), `solid`
- `docs/conventions.md` — package structure this project uses

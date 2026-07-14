---
name: jpa
description: JPA/Hibernate for PostgreSQL done safely — entity mapping, fetch strategy, N+1 prevention, optimistic locking, and the JPA/domain boundary.
---

# JPA

## Purpose

Use JPA as a persistence adapter — correct mappings, predictable SQL, no lazy-loading surprises — while keeping the domain model in charge.

## When to Use

Mapping entities, writing repositories/queries, diagnosing N+1 or LazyInitializationException, adding locking.

## Rules

1. **LAZY everywhere**: every association `fetch = FetchType.LAZY` (including `@ManyToOne`, which defaults EAGER). Data needed together is fetched explicitly per query.
2. **Fetch explicitly per use case**: `join fetch` / `@EntityGraph` for read paths that need associations; projections (records via constructor expressions) for list views — never load entities to map them to 5 fields.
3. **No OSIV** (`open-in-view: false`, see skill `spring-boot`): a `LazyInitializationException` means the query was wrong, not that OSIV is missing.
4. **Optimistic locking** (`@Version`) on every aggregate with concurrent writes: cart, stock, order. Map `OptimisticLockException` → 409 for retryable client conflicts. `PESSIMISTIC_WRITE` only for stock decrement hot spots, per ADR (see skill `inventory`).
5. **Equality**: entities compare by id with the proxy-safe pattern (`getClass()`-tolerant equals, constant hashCode or id-based once assigned) — never by mutable fields, never Lombok `@Data` on entities.
6. **IDs**: generation strategy per project ADR (`identity` for bigint, or app-generated UUIDv7). No `AUTO` defaults.
7. **Schema is Flyway's** (`ddl-auto: validate`); entity changes require a migration from database-engineer first.
8. Collections owned by the aggregate: `orphanRemoval = true` + cascade on parent→child compositions (`Order` → lines); never cascade across aggregates.

## Examples

```java
// list view: projection, not entities
public record ProductListItem(UUID id, String name, BigDecimal price) {}

@Query("""
    select new com.ecommerce.catalog.application.ProductListItem(p.id, p.name, p.price)
    from ProductEntity p where p.category.id = :categoryId
    """)
Page<ProductListItem> findByCategory(UUID categoryId, Pageable pageable);

// detail view: explicit fetch
@EntityGraph(attributePaths = {"lines", "lines.product"})
Optional<OrderEntity> findWithLinesById(UUID id);
```

## Best Practices

- Log SQL in dev (`logging.level.org.hibernate.SQL: debug`) and make "count the queries" part of code review for new endpoints; test-engineer can assert query counts on hot paths.
- Batch writes where volume exists (`spring.jpa.properties.hibernate.jdbc.batch_size`).
- Read-only queries in `@Transactional(readOnly = true)` use cases (see skill `transactions`).
- Prefer `getReferenceById` over `findById` when only setting an association (skips a SELECT).

## Common Mistakes

- N+1 from iterating a lazy collection in a mapper/loop — fetch it in the query.
- `CascadeType.ALL` + `orphanRemoval` on `@ManyToOne` or across aggregates (deletes shared data).
- Bidirectional associations everywhere "for navigation" — map the owning side; add the inverse only when a real query needs it.
- Modifying entities outside a transaction and wondering why nothing persisted (or worse, relying on accidental dirty checking inside one).
- Using JPA for bulk operations row-by-row — delegate bulk updates to a modifying query or SQL via database-engineer.

## References

- Vlad Mihalcea, *High-Performance Java Persistence*; Hibernate 6 docs
- See skills `transactions`, `postgresql`, `query-optimization`, `clean-architecture`

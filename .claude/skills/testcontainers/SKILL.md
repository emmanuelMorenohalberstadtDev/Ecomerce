---
name: testcontainers
description: Integration testing against real PostgreSQL with Testcontainers — singleton container pattern, Spring wiring, migration testing, and state isolation per test.
---

# Testcontainers

## Purpose

Integration tests run against the real database engine — the one production uses — so SQL, migrations, constraints, and locking behave exactly as they will in production.

## When to Use

Testing repositories/adapters, Flyway migrations, queries with Postgres-specific behavior, transactional semantics. NOT for domain logic (plain JUnit) or endpoint contracts without persistence (MockMvc slices).

## Rules

1. **Real engine, pinned version**: `postgres:16-alpine` (match the compose/prod version exactly). H2-with-Postgres-mode is forbidden — it lies about constraints, locking, and SQL dialect.
2. **Singleton container per JVM**: one static container shared across all integration tests — startup cost paid once, not per class. Spring wiring via `@ServiceConnection` (Boot 3.1+).
3. **Isolation by state, not by container**: each test starts from known state — `@Transactional` rollback for repository slices, or truncate-all + reseed in `@BeforeEach` for tests that must commit (locking, after-commit events).
4. **Migrations are tested here**: the container schema comes from Flyway running the real migrations — never `ddl-auto: create`. A migration that fails in Testcontainers fails before it fails anywhere real.
5. **Tag and tier**: `@Tag("integration")`; CI runs unit first, integration second (see skill `github-actions`). Keep the integration suite focused — repositories and adapters, not every use case.
6. Test the Postgres-specific things unit tests can't: unique/check constraint violations mapping to the right exceptions, optimistic-lock conflicts, keyset pagination queries, `on delete` behavior.

## Examples

```java
// single shared base — all integration tests extend it
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");
    static { POSTGRES.start(); }   // singleton: started once, reused, reaped by Ryuk
}
```

```java
class JpaOrderRepositoryIT extends IntegrationTest {
    @Autowired OrderRepository orders;

    @Test
    void shouldRejectDuplicateIdempotencyKey_TC9() {
        orders.save(OrderMother.placed().withIdempotencyKey("k1"));

        assertThatThrownBy(() -> orders.save(OrderMother.placed().withIdempotencyKey("k1")))
            .isInstanceOf(DataIntegrityViolationException.class);  // uq constraint, real DB
    }
}
```

## Best Practices

- Concurrency tests live here: two threads decrementing the same stock row proves the locking strategy (see skill `inventory`) — impossible to prove with mocks.
- Keep one seeded "realistic volume" dataset script for query-plan-sensitive tests (coordinate with performance-engineer).
- Reuse the base class's context: avoid `@DirtiesContext` and per-class `@DynamicPropertySource` variations that fragment the context cache and multiply startup time.
- Docker required locally and in CI — devops-engineer guarantees the daemon in the pipeline.

## Common Mistakes

- One container per test class (10× slower suites for zero isolation gain — state isolation is the job of truncation/rollback).
- `@Transactional` on tests that verify commit-time behavior (constraints deferred, after-commit listeners never fire, everything "passes").
- Letting the app config leak (`ddl-auto: update` in test profile) so tests pass on a schema Flyway never made.
- Asserting on auto-generated IDs across tests that share sequences.
- Skipping integration tests locally "because slow" and discovering constraint bugs in CI — keep the suite fast enough to run.

## References

- java.testcontainers.org; Spring Boot `@ServiceConnection` docs
- See skills `flyway`, `jpa`, `junit`, `github-actions`

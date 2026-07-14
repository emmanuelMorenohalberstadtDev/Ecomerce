---
name: junit
description: JUnit 5 practices — test structure and naming, parameterized tests, lifecycle discipline, assertions, and deterministic time/randomness.
---

# JUnit

## Purpose

Write unit tests that read as specifications: one behavior each, deterministic, fast, and named so a failure alone tells you what broke.

## When to Use

Writing any Java test; structuring test classes; choosing assertion and parameterization styles.

## Rules

1. **Naming states the rule**: `should<Outcome>_when<Condition>` (+ plan case ID when it exists): `shouldRejectOrder_whenCartIsEmpty_TC3`. Class mirrors the subject: `PlaceOrderUseCaseTest`.
2. **One behavior per test**: a test asserting placement *and* pricing *and* events is three tests. Multiple assertions about *one* outcome are fine (use `assertAll`/AssertJ chains).
3. **Arrange-Act-Assert** with blank-line separation; arrangement over ~5 lines moves to a builder/mother (see test data rules in `templates/test-plan.md`).
4. **Determinism is non-negotiable**: inject `Clock` (`Clock.fixed(...)`) — never `Instant.now()` in tested logic; seed randomness; no `Thread.sleep` (Awaitility for async); no order dependence (`@TestMethodOrder` is a smell outside true lifecycle suites).
5. **Parameterized tests for boundary tables**: `@ParameterizedTest` + `@CsvSource`/`@MethodSource` for quantity 0/1/999/1000-style cases from the test plan — not ten copy-pasted methods.
6. **AssertJ** as the assertion library: `assertThat(order.status()).isEqualTo(PLACED)`; exceptions via `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)` — assert on type + stable data, not full message strings.
7. **Lifecycle**: fresh state per test (default per-method instance); `@BeforeEach` only for setup shared by *all* tests in the class; no mutable static state.
8. `@Nested` classes to group behaviors of one method/scenario when a class grows (`class WhenCartIsEmpty { ... }`).

## Examples

```java
class QuantityTest {

    @ParameterizedTest(name = "quantity {0} is rejected")
    @ValueSource(ints = {0, -1, 1000})
    void shouldReject_whenOutOfRange(int value) {
        assertThatThrownBy(() -> new Quantity(value))
            .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void shouldCreate_whenWithinRange() {
        assertThat(new Quantity(999).value()).isEqualTo(999);
    }
}
```

```java
@Test
void shouldExpireCart_whenTtlElapsed_TC12() {
    var clock = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), UTC);
    var cart = CartMother.activeCart(clock.instant().minus(Duration.ofDays(8)));

    var expired = cart.isExpired(clock);

    assertThat(expired).isTrue();
}
```

## Best Practices

- Verify each new test fails when the behavior is broken (author-time mutation check) — a test that can't fail is decoration.
- Test names + `@Nested` structure should let `mvn test` output read like the spec of the class.
- Keep the unit suite fast (< 30s locally): no Spring context, no I/O — that's the integration tier (see skill `testcontainers`).
- `@Tag("unit")` / `@Tag("integration")` so CI tiers run separately.

## Common Mistakes

- Asserting on exact user-facing message strings (breaks on copy edits; assert types/codes).
- `@SpringBootTest` for logic testable with `new` — 100× slower for zero extra confidence.
- Shared fixtures mutated across tests (the classic Tuesday-only failure).
- Testing private methods via reflection — test through the public API or extract a class.
- Empty catch + `fail()` patterns predating `assertThatThrownBy`.

## References

- junit.org/junit5 user guide; AssertJ docs
- See skills `mockito`, `testcontainers`, `testing-strategy`, `jacoco`

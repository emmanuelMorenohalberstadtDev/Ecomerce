---
name: mockito
description: Test doubles with Mockito — what to mock and what never to mock, stubs vs verification, argument captors, and fakes as the better alternative.
---

# Mockito

## Purpose

Isolate the unit under test from its collaborators without welding tests to implementation details — mock the boundary, verify the behavior.

## When to Use

Unit-testing use cases and services with ports/collaborators. NOT for repositories against a DB (Testcontainers), NOT for value objects/domain entities (use real ones — they're the point).

## Rules

1. **Mock only owned boundaries**: ports (`PaymentGatewayPort`, `CartRepository`) and collaborators of the unit. Never mock: domain objects, value objects, DTOs, `List`/`Optional`, or types you don't own (mock your port wrapping them instead).
2. **Prefer fakes for state, mocks for protocol**: an `InMemoryCartRepository` (a real class in `src/test`) beats stubbing `findById/save` pairs — it verifies behavior across calls. Reserve Mockito for interaction checks (was the gateway charged exactly once?).
3. **Stub what the test needs, nothing more**: unnecessary stubbings are failures (`@MockitoSettings(strictness = STRICT_STUBS)` — keep it on). Every `when(...)` in a test must matter to its assertion.
4. **Verify sparingly — outcomes over interactions**: assert the returned result/saved state first; `verify` only for side effects invisible in state (event published, email sent). `verify` on every stubbed call is coupling, not coverage.
5. **Argument captors for rich assertions**: capture the saved aggregate and assert on its state, rather than `any(Order.class)`.
6. **No mocking statics/constructors/final as a routine** (`mockStatic` is a red flag): needing it means a seam is missing — report to the lead (test-engineer's constraint), inject the dependency.
7. Matchers all-or-nothing per call (`eq()` around literals when any matcher is used).

## Examples

```java
@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {
    @Mock PaymentGatewayPort gateway;
    CartRepository carts = new InMemoryCartRepository();   // fake for state
    @InjectMocks PlaceOrderUseCase useCase;                // or build by hand

    @Test
    void shouldChargeExactTotal_whenOrderPlaced_TC4() {
        carts.save(CartMother.withItems(2, Money.ars("1500.00")));
        when(gateway.charge(any(), any(), any())).thenReturn(PaymentResult.approved(ref()));

        useCase.execute(new PlaceOrderCommand(CUSTOMER_ID, TOKEN));

        var amount = ArgumentCaptor.forClass(Money.class);
        verify(gateway).charge(any(), amount.capture(), eq(TOKEN));
        assertThat(amount.getValue()).isEqualTo(Money.ars("3000.00"));
    }
}
```

## Best Practices

- Name mocks by role in the test narrative; if a test needs 6 mocks, the class under test has an SRP problem — report it.
- Stub failure modes explicitly (`thenThrow`, declined results) — sad paths are where money is lost (see `templates/test-plan.md` mandatory categories).
- `BDDMockito` (`given/willReturn`) is fine if used consistently project-wide — pick one style in conventions.

## Common Mistakes

- Over-specified tests: `verify` + `verifyNoMoreInteractions` on everything → every refactor breaks green behavior.
- Mocking the class under test (partial mocks/spies to skip "the hard part") — test the real thing or redesign it.
- Stubbing chains (`when(a.b().c())`) — Law of Demeter violation surfacing in tests.
- Returning mocks from mocks — a fake or builder is screaming to exist.
- Using Mockito where `new` works: a `Money` is constructed, not mocked.

## References

- site.mockito.org; *Growing Object-Oriented Software, Guided by Tests* (mock roles, not objects)
- See skills `junit`, `hexagonal` (fakes at ports), `testing-strategy`

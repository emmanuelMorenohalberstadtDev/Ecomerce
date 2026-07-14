---
name: testing-strategy
description: The project's test pyramid — what each level covers, where a given behavior gets tested, mandatory case categories, and cost/confidence trade-offs.
---

# Testing Strategy

## Purpose

Every behavior gets tested exactly once, at the cheapest level that can prove it — no gaps, no duplicate coverage across tiers.

## When to Use

Writing test plans (qa-engineer), deciding where a test belongs (test-engineer), reviewing test placement, arguing about E2E scope.

## Rules

1. **The pyramid, with placement rules**:
   - **Unit (majority)** — domain rules, use cases with fakes/mocks at ports, value-object invariants. Fast, no Spring, no I/O.
   - **Integration (Testcontainers)** — repository/adapter behavior, migrations, constraints, locking, after-commit events. Real Postgres only.
   - **API (MockMvc/WebTestClient)** — the HTTP contract: status codes, problem details, validation mapping, authZ rules per the auth decision table, serialization.
   - **E2E (minimal)** — one browse→cart→checkout→order money-path journey against the compose stack. Nothing else until the money path is rock solid.
2. **One home per behavior**: quantity-must-be-positive is a unit test on `Quantity` — not re-proven in API tests (there, one 400 case proves the *mapping*, not every boundary value).
3. **Mandatory categories** in every plan (from `templates/test-plan.md`): happy path per AC, validation failures, authN/authZ (anonymous/wrong role/wrong owner), boundaries, concurrency where state is shared, idempotency on money operations. "N/A" requires a written reason.
4. **Concurrency behaviors are integration-tier by definition** (real locks, real transactions): stock oversell, cart merge races, double-submit orders.
5. **A bug fix ships with a regression test that failed pre-fix** — at the lowest tier that reproduces it (see `templates/bug-fix.md`).
6. Test code is production-grade code: reviewed, conventioned, refactored — builders/mothers over copy-paste (a duplicated fixture is DRY debt like any other).

## Examples

Placement decisions for "add to cart":

| Behavior | Tier |
|----------|------|
| Quantity 0 rejected by `Quantity` VO | Unit |
| Adding same product twice merges lines | Unit (Cart aggregate) |
| Cart persists with optimistic `@Version` bump | Integration |
| Two parallel adds don't lose an update | Integration (2 threads) |
| `POST /carts/{id}/items` returns 201 + summary | API |
| Anonymous user gets 401; other user's cart 404 | API |
| Guest adds item, checks out, pays | E2E (part of the one journey) |

## Best Practices

- Write the sad paths first — declined payments and stock races cost money; happy paths are the easy 20%.
- Keep tiers independently runnable (`unit` / `integration` tags) with unit as the sub-minute default loop.
- Track escaped defects against the tier that should have caught them — that's how the strategy improves (qa-engineer's feedback loop).
- Delete tests that no longer pin any behavior — suites grow noise like code grows dead code.

## Common Mistakes

- The ice-cream cone: thin unit layer, everything proven through slow E2E flows that break daily.
- Testing the same validation at four tiers (maintenance × 4, confidence × 1).
- `@SpringBootTest` as the default hammer for things a constructor call can test.
- Skipping concurrency cases because "hard to test" — those are the production incidents, pre-booked.
- Treating flaky tests as noise to retry instead of defects to root-cause (test-engineer rule).

## References

- Martin Fowler, *Practical Test Pyramid*; *Growing Object-Oriented Software, Guided by Tests*
- See skills `junit`, `mockito`, `testcontainers`, `jacoco`

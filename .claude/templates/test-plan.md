# Test Plan: <feature / change>

> Owner: qa-engineer (authoring) → test-engineer (implementation) · Story:

## Objective

What this plan proves. Tie every section back to the acceptance criteria.

## Test Levels

| Level | Tooling | What it covers here |
|-------|---------|---------------------|
| Unit | JUnit 5 + Mockito | Domain rules, use cases |
| Integration | Testcontainers (PostgreSQL) | Repositories, migrations, adapters |
| API | Spring MockMvc / WebTestClient | Endpoint contracts, status codes, authZ |
| E2E (when applicable) | — | Critical user journey only |

## Test Cases

| ID | AC | Level | Case | Data / Preconditions | Expected |
|----|----|-------|------|---------------------|----------|
| TC-1 | AC-1 | Unit | | | |

Mandatory categories — every plan must state cases (or justify N/A) for:

- Happy path per acceptance criterion
- Validation failures (each invalid input → 400/422 with problem detail)
- AuthN/AuthZ: anonymous, wrong role, wrong owner
- Boundary values (0, 1, max, empty collections, max page size)
- Concurrency (when state is shared: stock, cart, order status)
- Idempotency / duplicate submission

## Test Data Strategy

Builders/mothers to use; no shared mutable fixtures; database state per test.

## Coverage Target

≥ 80% line coverage on domain + application (JaCoCo). Exclusions must be justified.

## Out of Scope

What is deliberately not tested and why.

## Quality Gates

- [ ] Every acceptance criterion maps to ≥ 1 test case
- [ ] test-engineer implemented all cases; IDs traceable in test names
- [ ] All green in CI; coverage report attached to PR

See `docs/quality-gates.md`.

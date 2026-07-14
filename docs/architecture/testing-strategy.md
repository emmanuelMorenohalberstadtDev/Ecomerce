# Testing Strategy

> Owner: qa-engineer · Date: 2026-07-13 · Status: living document
> Basis: skill `testing-strategy` (pyramid rules), `.claude/docs/quality-gates.md` (Gate 3),
> `backend-architecture.md`, `frontend-architecture.md`, `domain-model.md`,
> `security-architecture.md` §3.4 (auth decision table), `database-design.md` §8 (seed volumes).
> This document sets strategy and policy. Per-feature test plans (`.claude/templates/test-plan.md`)
> apply it; test-engineer owns implementation details at feature time.

## 1. The Pyramid — Placement Rules for This Architecture

Every behavior is proven **exactly once, at the cheapest tier that can prove it**. The tiers, bound
to this architecture:

| Tier | Scope in this project | Tooling & constraints |
|---|---|---|
| **Unit** (majority) | Domain rules per context (aggregates, VOs, domain services, the order state machine, `PriceCalculator`); application use cases with **fakes at the outbound ports** (in-memory repository/port implementations, per skill `mockito` rule 2). | JUnit 5 + AssertJ (+ Mockito for protocol checks). **No Spring context, no I/O.** Domain is framework-free (ArchUnit rule 1), so a constructor call is always enough. |
| **Integration** | Repository adapters, Flyway migrations, DB constraints, optimistic-lock (`@Version`) conflicts, atomic conditional UPDATE / check-and-increment behavior, `AFTER_COMMIT` event listeners, N+1 statement-count assertions (backend-architecture perf budgets). | Testcontainers **PostgreSQL only** — pinned to the compose/prod version; H2 forbidden (skill `testcontainers` rule 1). Schema comes from the **real Flyway migrations**, never `ddl-auto: create`. Singleton container; state isolation per test (§5). |
| **API** | The HTTP contract per endpoint: status codes, RFC 9457 Problem Details (`type` + `status`, never message text), Bean Validation → 400 mapping, serialization shapes, pagination envelope, and **one authZ case per row of the auth decision table** (anonymous → 401, wrong role → 403, wrong owner → 404, right principal → 2xx — security-architecture §3.4). | MockMvc / WebTestClient slices. Persistence not exercised here unless the contract requires it. |
| **E2E** | **Exactly one journey in v1**: the money path — browse → add to cart → checkout (recalc/confirm) → pay → order visible. Run against the Docker Compose stack. Nothing else joins this tier until the money path is rock solid. | Compose stack, real nginx/backend/DB. Selectors via `data-testid` only (§6). |

**One home per behavior.** A rule proven at one tier is not re-proven at another; upper tiers prove
only the *wiring/mapping*, not the rule again. Placement decisions for the representative feature
**add-to-cart**:

| Behavior | Tier | Why |
|---|---|---|
| Quantity 0 / negative / over per-line cap rejected by `Quantity` VO | Unit | Pure invariant, `new` suffices |
| Adding the same product twice merges lines; snapshot taken at add time | Unit (Cart aggregate) | Aggregate rule, no I/O |
| `AddItemUseCase` looks up product via catalog port, saves cart | Unit (fakes at ports) | Orchestration logic, ports faked |
| Cart persists with optimistic `@Version` bump | Integration | Real JPA + Postgres semantics |
| Two parallel adds to one cart lose no update | Integration (2 threads) | Real locking — see §4 |
| `POST /carts/{id}/items` → 201 + summary; invalid body → 400 problem detail | API | Contract + mapping (one 400 case proves the mapping, not every boundary) |
| Anonymous → 401; another customer's cart → 404 | API | AuthZ row from decision table |
| Guest adds item and completes checkout | E2E | Part of the one money-path journey |

Tiers are independently runnable: `@Tag("unit")` / `@Tag("integration")`; CI runs unit first
(sub-minute default loop), then integration, then API, then E2E.

## 2. Coverage Gates (JaCoCo — Gate 3, CI-enforced)

- **Floor: ≥ 80% line coverage on `com.ecommerce.*.domain.*` and `com.ecommerce.*.application.*`**,
  enforced by `jacoco:check` bound to `verify`; a red check blocks merge. This is the Gate 3 number;
  qa-engineer owns it and cannot lower it — only the user can accept that risk, in writing.
- **Branch coverage watched, approaching full, on**: pricing composition (`PriceCalculator`,
  rounding), promotion/coupon eligibility and stacking, stock reservation/decrement decisions, and
  the **order state machine** (every legal transition and every rejected one is a branch). A missed
  branch there is a missed business rule, not a statistic.
- Unit + integration execution data are **merged** before the report so adapters credited by
  Testcontainers runs count (skill `jacoco`).
- **Exclusions are structural only**, each justified in a POM comment: generated mappers,
  `@Configuration` classes, JPA `*Entity` boilerplate, pure-data records, the application main
  class. Excluding code *because it is hard to test* is forbidden — hard-to-test is a design
  finding to report, not an exclusion.
- Coverage is necessary, not sufficient: Gate 3 review reads assertion quality alongside the
  report. 80% of weak assertions passes the number and fails the gate.

## 3. Mandatory Case Categories (every test plan)

Every per-feature plan must state cases for **all** of the following, or mark a category **N/A with
a written reason**. Silence is a plan defect.

1. **Happy path** — ≥ 1 case per acceptance criterion.
2. **Validation failures** — each invalid input class → 400/422 with the expected problem `type`.
3. **AuthN/AuthZ** — anonymous, wrong role, wrong owner, right principal, matched **exactly** to
   the endpoint's row in the auth decision table (security-architecture §3.4). Wrong owner is 404,
   never 403. Plans quote the table row they cover; qa-engineer syncs rows with security-engineer.
4. **Boundaries** — boundary tables for numeric rules (quantity 0/1/max, `Money` 0.01 and
   never-below-zero totals, empty collections, page size at default/cap/over-cap-clamped).
5. **Concurrency** — required whenever shared state mutates. The three known hot spots from the
   domain model, pre-booked in any plan that touches them:
   - **Stock reservation last-unit race** (inventory, PO rule 6)
   - **Coupon cap race** (promotions, PO rule 9 — total and per-customer caps)
   - **Cart merge / concurrent cart mutation** (cart, PO rule 1 + `@Version`)
6. **Idempotency on money operations** — duplicate order placement and duplicate payment submission
   with the same idempotency key produce one order/one charge; the replay returns the original
   result, not an error and not a second effect.

Failure-mode cases are written **first** in every plan (declined payment, shortage report, expired
payment window, expired/reused token) — that is where an ecommerce loses money.

## 4. Concurrency Testing Policy

Concurrency behaviors are **integration-tier by definition**: they exist only with real locks,
real transactions, and real Postgres visibility rules — mocks cannot prove them (skill
`testcontainers`). These tests run without test-level `@Transactional` (commit-time behavior must
actually commit) and use truncate-and-seed isolation (§5).

Acceptance cases the PO rules imply, binding on the relevant plans:

- **Rule 6 — oversell**: given stock = 1, two checkouts reserve in parallel → **exactly one**
  reservation succeeds; the loser receives a per-line shortage report (a result, not an exception —
  backend-architecture §5); final stock is 0, never negative. Same shape for N units / N+1
  contenders.
- **Rule 9 — coupon caps**: given remaining total cap = 1 (and separately, per-customer cap
  reached), parallel redemptions → the cap is **never exceeded**; exactly one check-and-increment
  wins inside the redeeming transaction; the redemption count equals the cap afterward.
- **Cart merge / parallel mutation**: concurrent line updates on one cart lose no update
  (optimistic `@Version` conflict surfaces and is resolved per policy); merge-on-login during
  concurrent guest-cart activity leaves one consistent cart per the merge rules (quantities summed
  and capped, freshest wins on conflict).

Determinism rules: latches/barriers to force the interleaving, Awaitility for async outcomes —
never `Thread.sleep`. A concurrency case skipped as "hard to test" is a pre-booked production
incident and a Gate 3 finding.

## 5. Test Data Strategy

- **Builders / object mothers** (`CartMother`, `OrderMother.placed()`, …) are the only fixture
  mechanism; arrangement over ~5 lines moves into one. No copy-pasted fixture blocks, **no shared
  mutable fixtures** ever — a fixture mutated across tests is the classic Tuesday-only failure.
- **Per-test DB state**, two sanctioned modes:
  - `@Transactional` rollback — for repository/adapter slices where commit-time behavior is not
    under test. Cheap, default.
  - **Truncate-and-seed** in `@BeforeEach` — mandatory for anything that must actually commit:
    constraint violations, locking/concurrency (§4), `AFTER_COMMIT` event listeners (a rollback
    test on an after-commit listener silently proves nothing).
- **Fixed `Clock`** injected everywhere time matters (payment window, reservation expiry, cart
  TTL, freshest-wins merge) — `Clock.fixed(...)`; `Instant.now()` in tested logic is a finding.
- **Seeded randomness**; no assertions on auto-generated IDs across tests sharing sequences.
- **Realistic seed volumes** for plan-sensitive tests: any test whose value depends on the query
  plan (pagination depth, search filters, index usage) runs at the seed volumes mandated in
  `database-design.md` §8, including its skewed shapes — coordinated with performance-engineer.
  Functional tests stay on minimal seeds; volume is only for plan-sensitive cases.

## 6. Frontend Testing Approach (v1 scope)

Proportionate by design: **the heavy assurance lives server-side in v1** — prices, stock, coupon
validity, and authorization are backend truth the SPA never re-derives (frontend-architecture §1).

- **Component/unit tests** for logic seams: signal stores as plain classes with faked API services;
  `computed()` derivations (cart subtotal formatting, `canCheckout`, filter chips); interceptors
  and guards as plain functions; presentational components via inputs/outputs.
- **`data-testid` is the only selector contract** for anything DOM-touching (frontend-architecture
  §7) — never classes or text.
- Four-state completeness (loading/empty/error/success) is verified at review per the frontend UX
  contract; no dedicated visual-regression tier in v1.
- No frontend E2E beyond the single money-path journey (§1). No coverage floor on frontend code in
  v1; expanding frontend assurance is a v2 decision recorded here first.

## 7. Traceability & Gates

- Every feature gets a plan from `.claude/templates/test-plan.md` **before implementation** —
  plans written after code to match what was built are a Gate 3 rejection.
- **Stable case IDs** (`TC-n`) map ≥ 1:1 from acceptance criteria and appear in test method names
  (`shouldRejectOversell_whenLastUnitRaced_TC6`) so plan → test is grep-traceable both ways.
- **Gate 3 evidence attached to the PR**: the followed test plan (or link), CI-green run, JaCoCo
  report (line % on domain+application, named counter) with coverage on changed code, and — for
  concurrency-touching changes — the concurrency cases named. "Tested manually" is not evidence.
- **Bug fixes ship with a regression test that failed pre-fix**, at the lowest tier that
  reproduces the bug.
- **Escaped-defect loop**: every escape is recorded against the tier that should have caught it,
  with a root cause; each one produces a concrete plan-template or strategy amendment here. An
  escaped defect means the plan was wrong, not just the code.

## 8. Flakiness Policy

- **Zero tolerance on main.** A flaky test is a defect, never noise to retry; blanket CI retries
  are forbidden as a masking mechanism.
- On first flake: test-engineer root-causes **or quarantines with a ticket within one day** —
  quarantine means tagged out of the gate *and* tracked with an owner and deadline, never silently
  deleted or `@Disabled` without a ticket. qa-engineer verifies the ticket exists and the
  quarantine list stays near-empty; a growing quarantine list is a Gate 3 escalation to the
  orchestrator.
- Known flakiness sources are banned up front by this strategy: `Thread.sleep`, unfixed clocks,
  unseeded randomness, shared mutable fixtures, order-dependent tests, per-class containers.

---
Amendments to this document: qa-engineer proposes, orchestrator records; the coverage floor and
mandatory categories move only with explicit user sign-off.

# Coding Standards

> Owner: reviewer · Date: 2026-07-13 · Gate: every PR is held to this document at Gate 6.
> This consolidates `.claude/docs/conventions.md` and `.claude/docs/global-rules.md` for the repository; those remain the upstream sources — on any divergence, they win and this document gets corrected. Architecture-level rules are enforced mechanically where possible (see the ArchUnit rules in `backend-architecture.md`); everything here is checkable in review — no rule is aspirational.

## 1. Java standards (backend)

### Naming (upstream: `.claude/docs/conventions.md`)

| Artifact | Pattern | Example |
|---|---|---|
| Use case | `<Verb><Noun>UseCase` | `AddItemToCartUseCase` |
| Controller | `<Resource>Controller` | `CartController` |
| Repository port | `<Entity>Repository` | `OrderRepository` |
| JPA adapter | `Jpa<Entity>Repository` | `JpaOrderRepository` |
| Request DTO | `<Action><Resource>Request` | `CreateOrderRequest` |
| Response DTO | `<Resource>Response` | `OrderResponse` |
| Domain event | `<Noun><PastTenseVerb>Event` | `OrderPlacedEvent` |
| Exception | `<Problem>Exception` | `InsufficientStockException` |

### Construction & types

- DTOs and value objects are Java `record`s; domain aggregates are classes with private state and behavior methods (no public setters, no status setters — transitions are named methods).
- Constructor injection only; all injected fields `final`. Field/setter `@Autowired` is a **blocker**.
- Money: `BigDecimal` (scale rules per `domain-model.md` Money VO) + currency — `double`/`float` on any money path is a **blocker**, including tests.
- Time: `Instant`/`ZonedDateTime`, UTC storage, injected `Clock` in logic that reads "now".
- Sealed hierarchies for closed sets (order states, payment results); pattern matching over `instanceof` chains.

### Hygiene

- No `catch (Exception)` outside the global advice; no swallowed exceptions; no log-and-rethrow duplication (upstream: `exception-handling` skill, `backend-architecture.md` §error handling).
- No dead code, no commented-out blocks, no `TODO` without a ticket reference — each is a **should**, three or more in one PR is a **blocker**.
- Comments state *why* (invariants, non-obvious constraints), never *what* the next line does. Javadoc required on: public ports, domain aggregate public methods encoding business rules. Not required on: DTOs, mappers, obvious accessors.
- Size guidance (flag for justification, not hard caps): methods > 30 lines, classes > 300 lines, constructors > 5 dependencies (SRP smell — see `solid` skill).

## 2. TypeScript / Angular standards (frontend)

Upstream: `frontend-architecture.md` (structure, state, interceptors) — this section is the per-line review checklist.

- Strict TypeScript. `any` is a **blocker**; `@ts-ignore`/non-null assertions require a linked ticket in a comment.
- Standalone components, `ChangeDetectionStrategy.OnPush`, native control flow (`@if`/`@for`/`@switch`/`@defer`), `inject()`, `input()`/`output()` — deviations are **blockers** in new code.
- Files kebab-case (`cart-summary.component.ts`); selectors `app-` prefixed kebab-case.
- Signals: state in `signal()`, derivation in `computed()` (named as facts: `isEmpty`, `canCheckout`), mutations via named store methods (actions). An `effect()` that sets another signal is a **blocker** (see `angular-signals` skill).
- Templates: bindings only — no method calls doing work per CD cycle, no chained ternaries; `track` mandatory on every `@for` (**blocker** if missing); `data-testid` on interactive elements (**should**).
- No manual `subscribe()` where `toSignal`/`resource`/async pipe fits; any manual subscription must show its termination (`takeUntilDestroyed()`, `take(1)`) — unbounded subscription is a **blocker**.
- No new npm dependency without architect approval noted in the PR (**blocker**, upstream: global rules).

## 3. SQL / migration standards

Upstream: `database-design.md` §Flyway conventions and `.claude/skills/flyway`.

- Files `V<version>__<snake_case_description>.sql`, sequential, zero-padded; `R__` repeatables idempotent.
- Editing an applied migration is a **blocker**, no exceptions (checksum integrity).
- snake_case tables/columns; index/constraint prefixes `idx_/uq_/ck_/fk_`; every table carries `created_at`/`updated_at timestamptz`.
- Every migration PR includes the safety analysis from `.claude/templates/database-migration.md` (locks, backfill, rollback story) — absence is a **blocker**.
- Destructive changes follow expand–migrate–contract across releases; drop-and-stop-using in one release is a **blocker**.

## 4. Git standards

Upstream: `.claude/docs/conventions.md` §Git.

- Conventional Commits: `<type>(<scope>): <subject>` — types `feat fix refactor test docs chore perf ci build`; scopes `catalog cart checkout order payment inventory pricing promo auth user infra ui`.
- One logical change per commit; refactor and feature never mixed in one commit (**should** → **blocker** when it obscures review).
- Branches: `feature/<ticket>-<slug>`, `fix/…`, `refactor/…`.
- PR template (`.claude/templates/pull-request.md`) filled completely — an unfilled template is returned unread.
- `--no-verify`, skipped hooks, or threshold edits to pass CI: **blocker** (upstream: global prohibitions).

## 5. Review severity taxonomy

Every finding cites the violated rule (this doc, a skill, an ADR, or an architecture doc section). "I'd do it differently" is not a finding.

| Severity | Meaning | Examples from this stack |
|---|---|---|
| **Blocker** — must fix before merge | Correctness, security, architecture, or convention breach | `double` for money; controller importing a repository (ArchUnit-covered, but flagged if suppressed); missing `track` in `@for`; edited applied migration; endpoint without its auth decision table row (escalates to security-engineer) |
| **Should** — fix now or ticket before merge | Real debt, not breaking | 40-line method mixing mapping and rules; missing `data-testid` on a new button; duplicated Tailwind cluster on its third copy; missing `readOnly` on a query-only use case |
| **Nit** — author's choice | Style within the rules | Ordering of private methods; `var` vs explicit type where both are idiomatic; message wording in a log line |

## 6. The review checklist (run per PR)

1. **PR hygiene**: template complete; one logical change; commits conventional; gate evidence attached (test results, and security/performance verdicts when the diff touches those gates).
2. **Conventions**: sections 1–4 of this document against the diff.
3. **Architecture adherence** — split enforcement:
   - *ArchUnit-covered* (verify the build ran them, don't re-derive): framework-free domain, layer edges, port-only cross-context access, package cycles, repository-per-root, transactions-only-in-application, no controller→repository, JPA entities confined, AFTER_COMMIT listeners, shared-kernel isolation, append-only audit repository (`backend-architecture.md` §ArchUnit).
   - *Review-covered* (human judgment): right layer for the logic, aggregate boundaries respected, snapshot pattern where PO rules demand, no business rules leaking into controllers/adapters.
4. **Tests**: present for the change at the tier `testing-strategy.md` assigns; assertions meaningful (behavior, not interaction ceremony); regression test on every bug fix that fails pre-fix.
5. **Security-sensitive diff?** (auth, input handling, money, PII, logging): escalate to security-engineer's gate — never adjudicated in code review.
6. **Readability test**: a competent newcomer could understand the hunk without the author present. If understanding required asking, the finding is on the code.
7. **Scope discipline**: findings outside the diff's purpose become tickets, not "while you're at it" demands.

## 7. Contradictions found during consolidation

None. The upstream sources (`.claude/docs/conventions.md`, `.claude/docs/global-rules.md`) and the architecture documents are consistent as of this date. One earlier inconsistency — three references pointing to `docs/conventions.md` instead of `.claude/docs/conventions.md` in ADR-0001 and `architecture-overview.md` — was corrected during the Wave 4 consistency pass.

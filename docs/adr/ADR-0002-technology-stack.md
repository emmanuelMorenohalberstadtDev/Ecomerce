# ADR-0002: Confirm the fixed technology stack and choose the resource ID strategy

> Status: Accepted (ID sub-decision: **pending security-engineer review**) · Date: 2026-07-13 · Owner: software-architect

## Context

The stack is **fixed by project global rules** (`docs/global-rules.md`): Java 21 + Spring Boot 3
(Security, Data JPA), Angular 21 + TypeScript + Tailwind + Signals + RxJS, PostgreSQL 16+ + Flyway,
JWT auth, Docker + Compose + nginx, GitHub Actions. This ADR cannot change it; its job is to record
*why the stack holds* against real alternatives — so the choice is defensible, not merely imposed —
and to settle one genuinely open sub-decision the conventions leave to an ADR: the primary-key /
public-identifier strategy for user-visible resources (products, carts, orders, users).

Forces on the ID decision: order and cart IDs appear in URLs (`/api/v1/orders/{id}`); sequential
IDs make enumeration and IDOR probing trivial (OWASP A01) and leak business volume (order count);
but random keys fragment B-tree indexes and complicate debugging. Money correctness (cent-exact,
single currency) and the concurrency rules from Gate 0 also weigh on database choice.

## Decision

We will build on the fixed stack as specified, and we will use **UUIDv7 as both primary key and
public identifier** for user-visible aggregates (product, cart, order, user, coupon), generated
application-side. Internal/child rows (order lines, audit entries, event log) may use
`bigint identity` since they are never addressed directly by clients. The UUID sub-decision is
flagged **pending security-engineer review** before the first schema migration.

## Options Considered

| Option | Pros | Cons |
|--------|------|------|
| **Backend: Spring Boot 3 / Java 21 (fixed)** | Mature transactions (`@Transactional` + JPA optimistic/pessimistic locking) map directly onto the oversell/coupon invariants; Spring Security + JWT is well-trodden; records/sealed types/virtual threads fit DDD value objects and Clean layering; huge hiring/knowledge base | Verbose vs Node; slower startup; heavier images (mitigated by multi-stage builds) |
| Backend alt: Node/NestJS + TypeScript | One language across stack; fast iteration; NestJS mimics the same layering | Weaker transactional ecosystem (TypeORM/Prisma locking is less battle-tested for row-level contention); CPU-bound pricing/audit work fights the event loop; team rules and skills are Java-centric |
| **Frontend: Angular 21 (fixed)** | Batteries included (router, forms, DI, i18n) suits a large commerce app; Signals give fine-grained reactivity; opinionated structure keeps multiple agents consistent | Steeper learning curve; heavier initial bundle than a minimal React setup (mitigated with `@defer`/lazy routes) |
| Frontend alt: React + ecosystem | Larger ecosystem; flexible | Flexibility is a liability for multi-agent consistency — every concern (routing, forms, state) is a new dependency decision, i.e., more ADRs and more drift |
| **DB: PostgreSQL 16 + Flyway (fixed)** | Real ACID + row locks + `CHECK` constraints enforce rules 4/6/9 in one place; `numeric` for cent-exact money; partial/covering indexes for catalog search; Flyway gives audited, ordered schema history | Single-node write ceiling (far beyond v1 needs); operational tuning knowledge needed |
| DB alt: MySQL | Comparable relational features | Weaker `CHECK`/DDL-transaction story; no advantage that would justify deviating |
| DB alt: MongoDB | Flexible product documents | No multi-row ACID guarantees of the same strength for stock/coupon counters; order snapshots and audit trails are relational by nature — wrong tool for rules 6/9/11 |
| **Auth: stateless JWT (fixed)** | No server session state → any API replica behind nginx can serve any request; natural fit for SPA + separate API origin | Revocation is hard (needs short-lived access + refresh rotation); token theft blast radius — mitigations owned by security-engineer per `jwt` skill |
| Auth alt: server sessions + cookies | Trivial revocation; smaller attack literature | Sticky sessions or shared session store adds state to an otherwise stateless API; CSRF surface; complicates the nginx/Compose topology |
| C — do nothing (no stack decision recorded) | None | Stack would still be fixed but undefended; ID strategy would be improvised in the first migration and be near-impossible to change later |

### ID strategy sub-decision

| Option | Pros | Cons |
|--------|------|------|
| **A (chosen) — UUIDv7 PK = public ID for user-visible aggregates** | Non-enumerable URLs (IDOR probing gains nothing, order volume not leaked); time-ordered → B-tree locality close to bigint; generated app-side → aggregate has identity before persistence (fits DDD factories); one identifier, no mapping layer | 16 bytes vs 8 per key/FK; embedded timestamp leaks coarse creation time (acceptable — flagged to security-engineer); ugly in logs |
| B — bigint PK everywhere, exposed | Smallest, fastest keys; human-readable | Sequential public IDs invite enumeration/IDOR and leak business metrics — conflicts with priority 1 |
| C — bigint PK + separate random public UUID column | Optimal internal keys | Two identities per aggregate: every lookup translates, every join and log entry can confuse them; complexity not justified at v1 scale (KISS) |
| D — UUIDv4 | Maximum randomness | Random inserts fragment B-trees on hot tables (orders); v7 gives the same public unguessability in practice with better locality |

**Note**: authorization never relies on ID unguessability — every resource access enforces
ownership checks (`order.customerId == principal`) regardless. UUIDs reduce recon value; they are
not an access-control mechanism. This framing plus the UUIDv7-timestamp-leak trade-off is exactly
what security-engineer must sign off on.

## Rationale

Against the global priority order: **correctness/security** — PostgreSQL's transactional row
locking is the single strongest tool available for the Gate 0 concurrency rules, and `numeric`
money arithmetic is exact; UUIDv7 removes the cheapest recon vector on public URLs. **Maintainability**
— an opinionated, uniform stack (Spring + Angular) keeps nine contexts and multiple agents
structurally identical. **Testability** — Testcontainers runs the real database in CI; the
framework-free domain (ADR-0001) tests without any of this stack. **Performance/scalability** —
stateless JWT API scales horizontally behind nginx with zero session infrastructure; UUIDv7 keeps
index locality acceptable. Every alternative loses on priority 1 or 2 before performance is even
discussed.

## Consequences

- **Positive**: one coherent, well-documented stack; ID strategy decided before the first
  migration (cheapest possible moment); IDOR recon surface reduced by default.
- **Negative / accepted trade-offs**: ~2x key storage on user-visible tables; JWT revocation
  complexity delegated to the auth design; Java/Angular ceremony accepted for consistency.
- **Follow-up work created**:
  - security-engineer: review UUID sub-decision (timestamp leakage, ownership-check mandate) and
    the JWT lifetime/rotation design **before** the first Flyway migration and auth ADR.
  - database-engineer: apply UUIDv7 vs bigint per table per this ADR in schema design.
  - devops-engineer: multi-stage Docker builds for JVM + Angular per `docker` skill.

## Compliance

- Review checklist: any `pom.xml`/`package.json` dependency addition cites a written justification
  approved by software-architect (global rule).
- Flyway migration review: user-visible aggregate tables use `uuid` PKs; child/audit tables may use
  `bigint identity`; deviations cite an ADR.
- ArchUnit/CI: no alternative framework imports (e.g., no `javax.servlet` sessions — the API stays
  stateless; Spring Security config asserts `SessionCreationPolicy.STATELESS`).
- Controller review checklist: every by-ID endpoint enforces ownership/role authorization — ID
  unguessability is never cited as the control.

## Quality Gates

- [x] Numbered sequentially in `docs/adr/`, never reuses a number
- [x] All affected agents can act on this decision without asking follow-ups (ID sub-item blocks
      only the first migration, pending security-engineer)
- [x] Superseded ADRs updated with a pointer to this one (none)

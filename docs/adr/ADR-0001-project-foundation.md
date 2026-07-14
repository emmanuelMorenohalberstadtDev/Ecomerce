# ADR-0001: Adopt a modular monolith with bounded contexts and Clean/Hexagonal layering

> Status: Accepted · Date: 2026-07-13 · Owner: software-architect

## Context

We are starting an ecommerce system from zero application code. The product-owner scope (Gate 0)
fixes v1 as: catalog with search/filter, guest + authenticated carts with merge-on-login, checkout
with stock reservation and a 15-minute payment window, an explicit one-directional order lifecycle,
auth/accounts, and a basic audited admin — with promotions/coupons as a SHOULD whose *architecture*
must be accommodated from day one even though delivery is deferred.

The binding business rules are concurrency- and consistency-heavy: exactly one winner on the last
unit of stock, coupon caps never exceeded under concurrency, server-side price authority with
customer re-confirmation, and immutable price snapshots on orders. These demand strong transactional
guarantees and testable, framework-free business rules.

The stack is fixed by global rules (Java 21, Spring Boot 3, PostgreSQL, Angular 21, Docker). Team
size is small (agent roles, single repository). Global rules mandate SOLID, Clean Architecture,
YAGNI, and forbid designing for hypothetical scale. We need a structural decision **now** because
every subsequent artifact (schema, API, packages, CI) hangs off it.

## Decision

We will build a **modular monolith**: one deployable Spring Boot application partitioned into nine
bounded contexts (`catalog`, `cart`, `checkout`, `order`, `payment`, `inventory`, `pricing`,
`promotions`, `auth`) plus a minimal `shared` kernel. Each context is internally layered per
Clean Architecture (`domain` / `application` / `infrastructure` / `presentation`) with Hexagonal
ports & adapters at its edges. Contexts communicate only through application-layer ports
(synchronous needs) and after-commit domain events (reactive effects) — never through shared
entities or repositories.

## Options Considered

| Option | Pros | Cons |
|--------|------|------|
| **A (chosen) — Modular monolith, bounded contexts, Clean/Hexagonal layers** | One deployment and one DB → ACID transactions solve oversell/coupon-cap concurrency directly; context boundaries keep coupling low and match business language; domain layer testable without Spring; contexts extractable later if a real need appears; matches fixed stack with zero extra infrastructure | More upfront discipline than a plain layered app; boundary rules must be mechanically enforced (ArchUnit) or they erode; some ceremony (ports, mappers) for simple features |
| B — Microservices per context | Independent deploy/scale per context; enforced boundaries by network | Distributed transactions for stock/coupon invariants (sagas, outboxes, idempotency) massively raise correctness risk for rules 6 and 9; operational cost (service discovery, per-service CI, observability) unjustified at current scale; violates the global-rules ban on designing for hypothetical scale; slower to deliver v1 |
| C — Layered-by-technical-type monolith (`controllers/`, `services/`, `repositories/`) | Simplest to start; familiar to any Spring developer | Business rules smear across giant service classes; no seam for the promotions SHOULD; every feature touches every layer package → merge conflicts and creeping coupling; "where does this logic go?" has no answer; hardest to test domain rules in isolation |
| D — Do nothing (no declared structure, grow organically) | Zero upfront cost | Structure emerges anyway — as accidental coupling; PO's concurrency rules end up half-enforced in controllers; refactoring cost compounds; contradicts global rules (Clean Architecture is mandatory) |

## Rationale

Ranked against the global-rules priority order:

1. **Correctness & security** — the decisive criterion. Rules 6 (exactly one oversell winner) and
   9 (coupon caps under concurrency) are trivially correct inside one PostgreSQL database using
   row-level locking / atomic conditional updates in a single transaction. Option B turns each of
   these into a distributed-consistency problem — the highest-risk way to satisfy the strictest
   requirements. Option C/D correctness erodes because invariants have no owning aggregate.
2. **Maintainability** — contexts named in business language mean any developer locates logic by
   asking "which business area?" (success criterion: < 1 minute via the context map). Option C
   answers only "which technical kind?", which scales badly with feature count.
3. **Testability** — framework-free domain + ports means domain and use-case tests run without
   Spring, the database, or the network; the ≥ 80% coverage target on domain/application becomes
   cheap. Options C/D require Spring context or mocks-everywhere tests.
4. **Performance** — in-process calls between contexts; no serialization or network hops (B loses).
5. **Scalability** — a modular monolith scales vertically and horizontally (stateless app behind
   nginx) far beyond v1 needs; if a context ever needs independent scaling, its port/event
   boundaries are the extraction seam. We are explicitly *not* building that now (YAGNI).

The promotions SHOULD is satisfied structurally, not speculatively: `pricing` owns a
`PromotionPolicyPort` from day one; v1 ships a null/no-discount adapter, and delivering promotions
later means implementing the adapter and the `promotions` context — no reshaping of cart, checkout,
or order.

## Consequences

- **Positive**:
  - Single transaction = single aggregate rule is enforceable and sufficient for all PO invariants.
  - One Docker image, one Compose service for the API — devops surface stays small.
  - Every context has an identical internal shape (`.claude/docs/conventions.md` package layout), so
    conventions, ArchUnit rules, and reviews are uniform.
  - Deleting or extracting a context touches only its package and its ports.
- **Negative / accepted trade-offs**:
  - Boundary discipline is not enforced by the compiler across packages — it *will* erode without
    automated checks; we accept the cost of writing and maintaining ArchUnit rules.
  - Ports + per-boundary mapping add ceremony to trivial flows; proportionality exceptions must be
    recorded per ADR, not improvised.
  - One shared database means schema-level coupling is possible; mitigated by per-context table
    ownership (a context's tables are written only by its own adapters).
- **Follow-up work created**:
  - ADR-0002: technology stack + ID strategy.
  - Context map and domain model (`docs/architecture/`).
  - ArchUnit test module skeleton (backend-lead, first backend PR).
  - Event publication pattern decision (after-commit via `@TransactionalEventListener`; formalize
    in a follow-up ADR when the first cross-context event is implemented).

## Compliance

Enforced mechanically once code exists, via an ArchUnit test suite that is a required CI check:

1. `no classes in ..domain.. may depend on classes in [org.springframework.., jakarta.persistence.., com.fasterxml..]` — domain is framework-free.
2. Layer rule per context: `presentation → application → domain`; `infrastructure → application|domain`; no inverse edges; no `presentation → infrastructure`.
3. Cross-context rule: classes in `com.ecommerce.<A>` may depend on `com.ecommerce.<B>` **only** via
   `..<B>.application.port..` interfaces or `..<B>.domain.event..` types — never repositories,
   entities, or use-case classes of another context.
4. Cycle check: `slices matching com.ecommerce.(*).. should be free of cycles`.
5. Repository interfaces exist only in `domain`, one per aggregate root (review checklist item).
6. Review checklist: every new cross-context call names the port it goes through; any exception to
   layering cites an ADR number.

## Quality Gates

- [x] Numbered sequentially in `docs/adr/`, never reuses a number
- [x] All affected agents can act on this decision without asking follow-ups
- [x] Superseded ADRs updated with a pointer to this one (none — first ADR)

# ADR-0005: Payment drives order's `PLACED→PAID` transition synchronously through a new `order.application.port` façade; declines/timeouts never touch order state; refund-on-cancel stays event-driven

> Status: Accepted · Date: 2026-08-02 · Owner: software-architect

## Context

`payment` is about to be implemented (in-scope: `Payment`/`PaymentAttempt`/`Refund`, `SubmitPaymentUseCase`,
`PaymentGatewayPort` + simulated adapter, refund-on-cancel). `auth`, `catalog`, `cart`, `pricing`,
`inventory`, `checkout`, `order` are already implemented and committed. ADR-0003 established the
cross-context recipe (producer `application.port` façade + consumer's own independently-named
`domain.port.out` mirror, producer publishes synchronously inside its transaction, consumer
subscribes with `@TransactionalEventListener(AFTER_COMMIT)`). ADR-0004 applied it to order↔inventory
and additionally ruled, twice, that a resource's state-owning transitions must have exactly **one**
synchronous caller, never a listener, because two independent transitioners/releasers racing the
same resource is a correctness risk this codebase has already chosen not to carry.

Both prior ADRs flagged payment as a known follow-up but did not decide the payment↔order seam
itself:

- ADR-0003 item on follow-ups: `PaymentGatewayPort` "follows this same recipe... no new pattern
  decision needed" — this is about payment's own **outbound** gateway port (payment → simulated
  PSP), a different question from payment↔order.
- ADR-0004 follow-up: "When payment is implemented: it becomes `OrderCancelledEvent`'s first real
  subscriber (refund) — no new pattern, `@TransactionalEventListener(AFTER_COMMIT)` in
  `payment.application.listener`." This settles the **refund** direction only (order → payment,
  via event, on cancellation). It says nothing about the **charge** direction (payment → order, on
  a successful or failed payment attempt) — the two are not the same question and this ADR treats
  them separately.

Two concrete gaps confirmed against the committed source, not assumption:

1. **`order` has never been a façade producer.** Every prior context that needed to be called
   cross-context (`catalog`, `cart`, `pricing`, `inventory`) already exposes an `application.port`
   façade; `order` has zero files under `order.application.port` beyond the admin-only
   `AuditLogPort`/`CurrentActorPort`/`OrderAuditAction` trio (ADR-0004's own accepted, per-context
   duplication pattern) — it has only ever been a *consumer* of `inventory`'s and `catalog`'s
   façades (`ReservationAdapter`, `ProductCatalogAdapter`). Whichever direction this ADR picks for
   the charge path, if order is the callee, it needs its first-ever producer-side façade.
2. **`domain-model.md` §4's event-catalog row for the payment events is stale against the actual
   checkout implementation.** It reads: "`PaymentCapturedEvent`/`PaymentDeclinedEvent`/
   `PaymentRefundedEvent` — producer=payment — consumers: `— (audit; checkout consumes results
   synchronously in v1)`." This describes checkout gaining a payment step. It does not have one:
   ADR-0003's own in-scope list for checkout is `StartCheckoutUseCase`, `ConfirmRecalculatedTotalUseCase`,
   `ExpirePaymentWindowUseCase` only; the committed `checkout` package (confirmed by directory
   listing) contains no `SubmitPaymentUseCase`, no `PaymentGatewayPort` reference, no payment
   dependency of any kind. This is the same class of stale-doc-vs-actual-implementation gap ADR-0004
   already found and corrected twice in this same table — corrected here too (see Consequences).

`Order.markPaid(Actor, Instant)` (the only method that can move an order `PLACED → PAID`) currently
has exactly one caller, `AdminMarkOrderPaidUseCase`, gated `@PreAuthorize("hasRole('ADMIN')")` — not
reusable as-is for a payment-triggered transition (wrong actor semantics, wrong auth). The one
existing precedent for a *system*-actor transition, `FailOrderFromCheckoutExpiryUseCase`, is
listener-triggered but still executes synchronously, in its own transaction, with no competing
caller of `Order.fail` — it does not, by itself, resolve whether the *trigger* (listener vs direct
port call) is the right shape for payment, because the reservation-release precedent it shares that
shape with (ADR-0003 item 3) is about who releases stock, not about cross-context money/order
atomicity, which is the actual new risk here (see Rationale).

## Decision

We will:

1. **Payment initiates the charge and drives the success transition synchronously, through a new
   `order.application.port.OrderPaymentPort` façade — order's first-ever producer-side façade.**
   Mirrors `inventory.application.port.StockReservationPort`'s shape exactly (interface + nested
   projection/exception classes in one file, implemented by a thin `order.infrastructure.adapter`
   class delegating to order's own use cases, each call = that use case's own transaction — which,
   per ADR-0001's own premise, "one deployment and one DB", is the *same physical DB transaction*
   the calling payment use case is already running, joined via default Spring `REQUIRED`
   propagation, not a new one; see Rationale item 1).

   ```java
   package com.ecommerce.order.application.port;

   public interface OrderPaymentPort {
       OrderPaymentView getForPayment(OrderId orderId, CustomerId customerId);
       void markPaid(OrderId orderId);

       record OrderPaymentView(OrderId orderId, CustomerId customerId, OrderStatus status,
                                Money grandTotal, ReservationId reservationId) {}

       class OrderNotFoundException extends RuntimeException { ... }
       class OrderNotPayableException extends RuntimeException { ... } // order not currently PLACED
   }
   ```

   Implemented by `order.infrastructure.adapter.OrderPaymentAdapter`, delegating to two new order
   use cases:
   - `GetOrderForPaymentUseCase` — ownership-scoped read via the existing
     `OrderRepository.findByIdAndCustomerId` (no new repository method).
   - `MarkOrderPaidFromPaymentUseCase` — same body as `AdminMarkOrderPaidUseCase` (`order.markPaid`,
     `reservationPort.commit`, `orderRepository.save`, publish `OrderPaidEvent`) with `Actor.system()`
     instead of `Actor.admin(actorId)`, no `@PreAuthorize`, and **no `AuditLogPort` write** —
     mirroring `FailOrderFromCheckoutExpiryUseCase`'s already-established shape for system-actor
     transitions (security §6c's audit log is for *admin* actions specifically; a customer paying
     for their own order is not one).

   Payment defines its own, independently-named `domain.port.out.OrderPort` mirror (payment's own
   DTOs/exceptions), implemented by `payment.infrastructure.adapter.OrderAdapter` wrapping
   `OrderPaymentPort` — no pattern deviation from ADR-0003 item 2.

2. **Decline and timeout never call any order-side port.** `SubmitPaymentUseCase` reads the order
   view first; if `status != PLACED`, it rejects the attempt with its own domain exception
   (`OrderNotPayableException`, mapped to 422) **before** invoking the gateway — no charge is
   attempted against an order that can no longer be paid. If the order is `PLACED`, the gateway is
   invoked; on `DECLINED`/`TIMEOUT` the outcome is recorded on a new `PaymentAttempt` and the method
   returns normally (2xx) with that outcome in the body — the order stays `PLACED`, untouched, so
   the customer can retry within the payment window. `Payment.markPaid` is only called after a
   `PaymentGatewayPort` outcome of `APPROVED`.
3. **Payment never drives `PLACED → FAILED` or any `CANCELLED` transition, including for a
   terminal/non-retryable decline.** The sole owner of `PLACED → FAILED` remains checkout's own
   payment-window expiry sweep (`ExpirePaymentWindowUseCase` → `FailOrderFromCheckoutExpiryUseCase`,
   ADR-0003/ADR-0004) — unchanged, untouched by this ADR. A hard decline just means every retry
   within the window will keep failing until the window lapses and checkout's sweep fails the order;
   v1 has no product requirement for an immediate "this decline is unrecoverable, fail the order now"
   short-circuit. Giving payment a force-fail path would reintroduce exactly the two-independent-
   failers risk ADR-0004 already rejected once (Rationale item 1; deferred as explicit follow-up, not
   decided here — YAGNI, no stated current requirement).
4. **Refund-on-cancel stays exactly as ADR-0004 already settled it: event-driven, asynchronous
   relative to order's own transaction.** `payment.application.listener` subscribes to
   `order.domain.event.OrderCancelledEvent` via `@TransactionalEventListener(phase = AFTER_COMMIT)`
   and issues a refund. No new decision here — confirmed, not re-litigated. This is deliberately
   **not** symmetric with the charge direction (item 1): order does not need to wait for, or ever
   know, the refund's outcome — `Order.cancel()` already completed and committed before the refund
   is even attempted, so there is no atomicity requirement to protect on that side (see Rationale
   item 1 for why the charge direction has the opposite requirement).

**Sanity checks confirmed, no further decision needed:**

- `Payment.attempt()` reads the amount to charge exclusively from `OrderPaymentPort.getForPayment(...).grandTotal()`
  — never a client-supplied amount (security-architecture §1.3/§1.4 threat 2). `SubmitPaymentRequest`
  carries only `orderId`, no amount field.
- `OrderPaymentPort.markPaid` reuses `Order.markPaid`'s own guard (`InvalidOrderTransitionException`
  if not `PLACED`) rather than duplicating a status check in the write path — the read-time
  precondition check in item 2 above is a **fast-fail UX/cost optimization** (don't call the
  simulated gateway pointlessly), not the correctness guarantee; the guarantee is the aggregate's
  own invariant, translated by `OrderPaymentAdapter` into `OrderPaymentPort.OrderNotPayableException`
  exactly like `ReservationAdapter` already translates `StockReservationPort`'s nested exceptions.
- No new dependency the other direction: `order`'s code never imports anything from `payment` —
  `OrderPaymentPort` is order's own file, called by payment, exactly like `StockReservationPort` is
  inventory's own file called by order. The dependency graph stays one-directional
  (`payment → order → inventory`), matching the module diagram.

## Options Considered

| Option | Pros | Cons |
|--------|------|------|
| **A (chosen) — Payment calls a new synchronous `OrderPaymentPort` on approval only; declines/timeouts touch nothing; force-fail on hard decline explicitly out of scope; refund stays event-driven** | Charge + order transition + reservation commit are one atomic DB transaction (ADR-0001's "one DB → ACID" premise applies directly) — a customer is never left "charged" while their order silently stays `PLACED`; single, locatable entry point to `PLACED → PAID` from payment (admin path is separate and independently safe, guarded by `Order.markPaid`'s own state check against double-apply); zero change to checkout's already-implemented, already-correct expiry-sweep ownership of `FAILED`; reuses every established pattern (façade recipe, nested-exception translation, system-actor use case shape) | Order takes on its first producer-side façade (new file surface, but additive-only per ADR-0003's own accepted trade-off); `MarkOrderPaidFromPaymentUseCase` duplicates ~80% of `AdminMarkOrderPaidUseCase`'s body (accepted — same trade-off ADR-0004 already accepted for `FailOrderFromCheckoutExpiryUseCase` vs the admin cancel path) |
| B — Order subscribes to a `PaymentCapturedEvent` (listener-driven, symmetric with the refund direction) | Fully symmetric payment↔order relationship, no new façade needed on order's side, reuses the event-catalog rows domain-model.md already names | Breaks atomicity: `@TransactionalEventListener(AFTER_COMMIT)` runs in a **new**, separate transaction after payment's own transaction has already committed (`Payment` = `CAPTURED` on disk). If order's listener then fails to mark the order paid or to commit the reservation (a DB error, a bug, a lost race), the result is a payment the system believes succeeded and an order that never confirms — a worse, money-adjacent version of exactly the inconsistency ADR-0004 already ruled out for reservation commit/release, and one ADR-0001's single-DB/ACID premise exists specifically to avoid. Also reopens the "two independent things can transition `PLACED → PAID`" shape ADR-0004's rationale already rejected once (admin path + a hypothetical payment listener) |
| C — Payment also force-fails the order directly on a terminal/non-retryable decline (new `OrderPaymentPort.fail` method or similar) | Faster user-facing "this order failed" signal for unrecoverable declines (e.g. fraud block) instead of waiting for the payment window to lapse | Reintroduces the exact "two independent failers of the same order" race ADR-0004 explicitly rejected for reservation release, now for the order status itself — checkout's sweep and payment would both be able to fail the same order; no stated product requirement distinguishes retryable from terminal declines in v1 (YAGNI); if this becomes a real need later it requires its own ADR to arbitrate ownership, not a quiet extra caller added here |
| D — Do nothing; let backend-lead invent the seam per gap during implementation | Fastest to type | Same anti-pattern ADR-0004's own Option D rejected: an undocumented decision on money-adjacent, cross-context state is exactly the kind of gap this role exists to close before implementation, not after |

## Rationale

Ranked against the global-rules priority order:

1. **Correctness/security** — dominant driver, and the reason this needed its own ADR rather than a
   mechanical repeat of ADR-0003/0004. The payment↔order charge path has a stronger atomicity
   requirement than the order↔inventory precedent it extends: money has conceptually "moved"
   (`Payment.status = CAPTURED`, persisted) the instant a charge is approved, so the order's
   `PLACED → PAID` transition and the reservation commit that must accompany it cannot be allowed to
   fail *independently, after the fact, in a separate transaction* — that would leave a captured
   payment pointing at an order that never confirms, a real customer-facing and accounting-facing
   defect, not a style preference. Option A keeps the whole sequence (gateway call, `Payment`
   mutation, `Order.markPaid`, reservation commit) inside one physical DB transaction (ADR-0001's
   "one deployment, one DB → ACID transactions solve concurrency directly" premise, applied here to
   atomicity rather than concurrency): either all of it commits or none of it does. Option B breaks
   that guarantee by construction (`AFTER_COMMIT` = separate transaction, after payment's own commit
   is already durable). Rejecting a force-fail path for payment (item 3 / Option C) protects the
   already-settled single-owner-of-`FAILED` invariant (ADR-0004) the same way ADR-0004 protected
   single-owner-of-release — this codebase has now made that call three times (checkout-expiry-vs-
   inventory-scheduler, order-vs-listener-for-commit/release, and now payment-vs-checkout-expiry-
   for-`FAILED`), consistently in the same direction.
2. **Maintainability** — one documented entry point to `PLACED → PAID` from payment, matching the
   already-legible admin entry point; corrects `domain-model.md` §4's stale checkout-consumes-
   payment-synchronously line instead of leaving a second, contradictory story on record (same
   discipline ADR-0004 applied to the same document).
3. **Testability** — payment's use cases depend only on `payment.domain.port.out.OrderPort`
   (payment's own interface, trivially fakeable); order's new use cases depend only on interfaces it
   already owns or already has (`OrderRepository`, `ReservationPort`) — no new test-time dependency
   shape introduced.
4. **Performance** — in-process calls only, identical cost profile to every other port call in this
   codebase; no alternative considered added network cost. Noted as an accepted, explicitly-flagged
   *future* cost in Consequences: holding a DB transaction open across the gateway call is fine while
   that call is in-process/simulated, and stops being fine the day a real PSP replaces it.
5. **Scalability** — not a driver; noted only to confirm none was invented.

Every choice here reuses an established shape (façade + mirror, nested-exception translation,
system-actor use case) applied to a context pair with one genuinely new property (a money-adjacent
atomicity requirement) — that property, not a desire for novelty, is what makes Option A's decision
against Option B the substantive part of this ADR.

## Consequences

- **Positive**:
  - Payment and order stay consistent by construction: a `Payment` cannot be `CAPTURED` while its
    order is not `PAID`, and vice versa, because both changes are the same DB transaction.
  - Order gains a documented, deliberate producer-side façade for the first time — the shape any
    future context needing order (e.g. a future notifications context reading order status) can
    reuse instead of inventing a fifth way in.
  - Checkout's existing, already-correct expiry-sweep ownership of `PLACED → FAILED` is untouched —
    zero rework, zero new risk to an already-shipped path.
- **Negative / accepted trade-offs**:
  - `MarkOrderPaidFromPaymentUseCase` duplicates most of `AdminMarkOrderPaidUseCase`'s body. Accepted
    per the same precedent ADR-0004 already established for `FailOrderFromCheckoutExpiryUseCase`.
  - The whole charge sequence (gateway call included) executes inside one DB transaction. Harmless
    while `PaymentGatewayPort`'s only implementation is in-process and synchronous with no real
    latency. **Becomes a real cost the day a real PSP adapter replaces the simulator** — holding a
    row-locking DB transaction open across a network call to a real gateway is a lock-hold/latency
    problem. Flagged explicitly as follow-up, not solved now (no real PSP exists yet — solving it
    now would be designing for a future that isn't a stated current requirement).
  - Payment has no ability to force-fail an order on a terminal decline in v1 — a customer who gets
    an unrecoverable decline sees the order stay `PLACED` (retryable in the UI) until the 15-minute
    window naturally lapses, rather than an immediate "this failed" state. Accepted; flagged as
    follow-up if product requirements change.
- **Documentation corrections made alongside this ADR** (in scope for software-architect, who owns
  `domain-model.md`):
  - §1 `payment` paragraph: gains one sentence — "Drives the order's `PLACED → PAID` transition
    synchronously via `order.application.port.OrderPaymentPort` on a successful capture (ADR-0005);
    never transitions an order to `FAILED` or `CANCELLED` — checkout's expiry sweep remains the sole
    owner of `PLACED → FAILED` (ADR-0004)."
  - §4 event catalog: the `PaymentCapturedEvent`/`PaymentDeclinedEvent`/`PaymentRefundedEvent` row's
    consumer column corrected from "`— (audit; checkout consumes results synchronously in v1)`" to
    "`— (audit; order consumes a successful capture synchronously via a port call, not this event —
    ADR-0005)`" — checkout never gains a payment step; this line predated the actual checkout/order
    implementation and is corrected here for the same reason ADR-0004 corrected two other rows in
    this table.
- **Follow-up work created**:
  - When `payment` gets a real PSP adapter (replacing `SimulatedPaymentGatewayAdapter`): revisit the
    transaction-boundary decision in this ADR — the gateway call should move outside the open DB
    transaction (e.g. an outbox/saga shape), which will need its own ADR at that time. Not a v1
    concern.
  - If a future product requirement needs an immediate order-fail on a terminal/non-retryable
    decline: needs a new ADR to arbitrate a second potential owner of `PLACED → FAILED` against
    checkout's expiry sweep. Explicitly deferred, not decided here.

## Compliance

- Mechanically enforced by the existing `ArchitectureTest.contexts_communicate_only_through_ports_or_events`
  (rule 6, ADR-0001) — any `payment` class importing `order.application.usecase.*`,
  `order.infrastructure.*`, or `order.presentation.*` fails CI; any `order` class importing anything
  from `payment.*` fails CI (the dependency stays one-directional: `payment → order`).
- Review checklist addition (software-architect gate):
  - `SubmitPaymentUseCase` must call `OrderPort.getForPayment` and check `status == PLACED` *before*
    calling `PaymentGatewayPort` — flag in review if a gateway call precedes the precondition check.
  - `SubmitPaymentUseCase` must only call `OrderPort.markPaid` after a gateway outcome of `APPROVED`
    — never on `DECLINED`/`TIMEOUT`.
  - No `payment` code may call, or gain, any method that transitions an order to `FAILED` or
    `CANCELLED` — flag in review if such a method appears on `OrderPaymentPort` without superseding
    this ADR.
  - No `order` code may subscribe to any `payment.domain.event.*` type for the `PLACED → PAID`
    transition — flag in review if an `order.application.listener` subscribing to
    `PaymentCapturedEvent` appears without superseding this ADR.
  - `RefundOnOrderCancelledListener` (or equivalent) must use
    `@TransactionalEventListener(phase = AFTER_COMMIT)`, matching the one existing precedent
    (`CartMergeEventListener`) and ADR-0004's confirmation.

## Quality Gates

- [x] Numbered sequentially in `docs/adr/`, never reuses a number
- [x] All affected agents can act on this decision without asking follow-ups
- [x] Superseded ADRs updated with a pointer to this one (none superseded — this closes the
      payment↔order follow-up ADR-0003/ADR-0004 both flagged, and corrects `domain-model.md` §4's
      stale "checkout consumes results synchronously" line the same way ADR-0004 corrected two other
      rows in that table)

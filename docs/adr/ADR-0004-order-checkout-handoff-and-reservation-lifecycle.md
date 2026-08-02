# ADR-0004: Order hand-off carries line snapshots in the event; inventory gains a `commit` façade method; order drives its own reservation commit/release synchronously

> Status: Accepted · Date: 2026-08-01 · Owner: software-architect

## Context

`order` is about to be implemented (in-scope: `Order` aggregate, `PlaceOrderFromCheckoutUseCase`
listener, `MarkOrderPaidUseCase`, `ConfirmOrderUseCase`, `ShipOrderUseCase`, `DeliverOrderUseCase`,
`CancelOrderUseCase`, `FailOrderUseCase`). `checkout` (ADR-0003) and `inventory` are already
implemented and committed. ADR-0003 established the recipe order must follow — provider
`application.port` façade + consumer's own independently-named `domain.port.out` mirror, producer
publishes synchronously inside its transaction, consumer subscribes with
`@TransactionalEventListener(AFTER_COMMIT)` — but ADR-0003 scoped only checkout's three crossings
(cart, pricing, inventory) and the two checkout-authored events. Four concrete gaps surfaced
between what ADR-0003 and `domain-model.md` specify and what building order actually requires, all
confirmed against the committed source, not assumption:

1. **`CheckoutAwaitingPaymentEvent` carries no line data.** It has `grandTotal` only. `domain-model.md`
   §2 requires `OrderLine` to hold an immutable `LineSnapshot (name, unit price, qty)` per line
   (rule 3: an order is never re-priced, never re-reads catalog). The per-line data exists in memory
   at the publish site — `CheckoutReservationCoordinator.reserveAndMoveToAwaitingPayment` holds
   `saved.getRecalculatedTotal().lines()`, a `List<RecalculatedLine(ProductId, Quantity, Money
   unitPrice, Money lineTotal)>` — but only `grandTotal()` is put into the event. Product **name**
   is in neither `RecalculatedLine` nor `PriceCalculationPort.PricedLineView` nor
   `CartLinesPort.CartLineView` — confirmed by reading all three; name never flows through checkout
   at all.
2. **Inventory's façade exposes `reserve`/`release` only.** `domain-model.md`'s class diagram gives
   `StockReservation` both `commit()` and `release()` (HELD→COMMITTED / HELD→RELEASED), and
   `CommitReservationUseCase` already implements the HELD→COMMITTED transition plus the
   `stock_movements` ledger row (no domain event, by that class's own documented design) — but
   nothing calls it; `StockReservationPort` and its `StockReservationAdapter` only wire
   `ReserveStockUseCase`/`ReleaseReservationUseCase`.
3. **`domain-model.md` §4 contradicts itself on how order-side transitions reach inventory.** The
   `OrderCancelledEvent` row lists `inventory (restock)` as an event consumer. Two other rows in the
   same table describe the opposite shape for order's own transitions: `OrderPaidEvent`'s row already
   says "reservation commit is a synchronous checkout→inventory port call" (a residual naming slip —
   checkout does not own `markPaid`; this should read `order→inventory`), and
   `CouponRedemptionReversedEvent`'s row says reversal "is invoked via port by order-cancel flow".
   ADR-0003 item 3 also already settled this exact shape for the structurally-identical
   payment-window-expiry path: checkout calls inventory's release façade synchronously rather than
   relying on a listener, explicitly to avoid two independent releasers racing on the same
   reservation.
4. **`Order`'s "References out" column omits `ReservationId`.** Gaps 2 and 3 both require order to
   know which reservation to commit or release, so it must hold one.

## Decision

We will:

1. **Extend `CheckoutAwaitingPaymentEvent` with a `lines` field** — a new nested record
   `CheckoutAwaitingPaymentEvent.Line(ProductId productId, Quantity quantity, Money unitPrice, Money
   lineTotal)`, populated in `CheckoutReservationCoordinator.reserveAndMoveToAwaitingPayment` from
   `saved.getRecalculatedTotal().lines()`. This is a breaking change to the record's constructor, but
   the type has zero consumers today (`order` does not exist yet), so it is safe. Product name is
   **not** added to this event — it is not price-sensitive, and adding it would require checkout to
   take on a new catalog dependency it doesn't otherwise need. Instead, `order` resolves the name
   itself, per line, at listener time via a new `order.domain.port.out.ProductCatalogPort` — the
   third independently-named consumer mirror of `catalog.application.port.ProductLookupPort`
   (following `cart.domain.port.out.ProductCatalogPort` and `pricing`'s equivalent exactly; no new
   pattern). Because `findActiveById` is ACTIVE-only by design, a product retired in the narrow
   window between checkout's commit and order's `AFTER_COMMIT` listener firing returns
   `Optional.empty()`; the listener treats this as non-fatal and falls back to a placeholder name
   (e.g. `"Unavailable product " + productId`) rather than failing order placement — a reservation
   that was already committed by checkout must always produce an order.
2. **Add `void commit(ReservationId reservationId)` to `StockReservationPort`**, delegating to the
   already-implemented `CommitReservationUseCase` exactly as `release` already delegates to
   `ReleaseReservationUseCase` in `StockReservationAdapter` — additive method, additive constructor
   argument, translating `InvalidReservationStateException` the same way `release` already does.
3. **Order drives both reservation transitions synchronously through its own port, in its own
   transaction — never through a listener.** `order.domain.port.out.ReservationPort` (order's own
   mirror, `{commit, release}`) is implemented by `order.infrastructure.adapter.ReservationAdapter`
   wrapping `inventory.application.port.StockReservationPort`. `MarkOrderPaidUseCase` calls
   `.commit(reservationId)`; `CancelOrderUseCase` and `FailOrderUseCase` call
   `.release(reservationId)` — all inside the use case's own `@Transactional` method, mirroring
   ADR-0003 item 3's rationale exactly (one owner per release/commit call, no competing listener
   racing it). `OrderPaidEvent`, `OrderCancelledEvent`, and `OrderFailedEvent` are still published
   afterward for audit trail and `OrderCancelledEvent`'s documented future `payment` (refund)
   consumer — inventory never subscribes to any of the three. `domain-model.md` §4 is corrected
   (see Consequences) to remove the contradiction rather than leave two conflicting descriptions on
   record.
4. **`Order` gains a nullable `ReservationId reservationId` field**, captured from
   `CheckoutAwaitingPaymentEvent.reservationId()` when the order listener creates the `Order` in
   `PLACED`. `domain-model.md` §2's "References out" column for `order` is corrected to list it
   (see Consequences).

**Sanity checks confirmed, no further decision needed:**
- `CheckoutSessionExpiredEvent` → order's `PLACED→FAILED` transition must **not** call
  `ReservationPort.release` again — checkout already released synchronously per ADR-0003 item 3
  before publishing that event; a second release call would hit `InvalidReservationStateException`
  (already RELEASED) and would violate the single-owner-of-release rule item 3 restates.
- No new auth/customer façade for v1 — `Order` holds `CustomerId` by reference only; the current
  model has no shipping-address VO, so the order listener never calls into `auth`.
- Order's admin lifecycle endpoints (`markPaid`, `confirm`, `ship`, `deliver`, `cancel`, `fail`)
  get their own `order.application.port.{AuditLogPort, CurrentActorPort}` and an `OrderAuditAction`
  enum, duplicated per context exactly like `inventory`'s (and `catalog`'s) already-established,
  documented-as-deliberate pattern. This repeats a settled pattern; it is not a new decision and
  needed no ruling here.

## Options Considered

| Option | Pros | Cons |
|--------|------|------|
| **A (chosen) — Event carries priced line snapshots (minus name); order resolves name via catalog's façade; inventory gains `commit`; order calls commit/release synchronously through its own port** | Order never re-derives price (rule 3 holds exactly); zero risk of a price-mismatch between what the customer confirmed/reserved and what gets snapshotted, since the event is built from the same in-memory `RecalculatedTotal` the reservation was made from; reuses `CommitReservationUseCase` and `ProductLookupPort`, both already built; matches ADR-0003's own precedent for who releases | `CheckoutAwaitingPaymentEvent`'s constructor changes (accepted — zero consumers exist); order takes on two new port dependencies (`ReservationPort`, `ProductCatalogPort`) instead of one |
| B — Order re-fetches cart lines via a new `CartReadPort` mirror and re-prices via `PriceCalculationPort` at listener time | No event payload change | Directly contradicts rule 3 ("never re-priced, never re-reads catalog") and the snapshot-immutability invariant; a price move in the gap between checkout's commit and the `AFTER_COMMIT` listener firing would silently produce an order that doesn't match what was actually reserved — a correctness bug, not a style choice |
| C — Publish `OrderCancelledEvent`/`OrderFailedEvent` and have inventory subscribe (as `domain-model.md`'s current `OrderCancelledEvent` row literally reads) | No new port method on `StockReservationPort`; matches the table as originally written | Two independent releasers (order's future listener and any other future caller) can race on the same reservation — exactly the failure mode ADR-0003 item 3 already rejected once; also inconsistent with the same table's `OrderPaidEvent`/`CouponRedemptionReversedEvent` rows, which already assume the synchronous-port shape |
| D — Do nothing; let `backend-lead` invent an answer per gap during implementation | Fastest to type | Each of the four gaps has a real correctness or consistency consequence (price integrity, a dead-end port, a self-contradicting doc, an unrepresentable aggregate field); "invent it while implementing" is exactly how undocumented decisions accumulate — the anti-pattern this role exists to prevent |

## Rationale

Ranked against the global-rules priority order:

1. **Correctness/security** — this is the dominant driver. Option A is the only one that keeps
   rule 3 ("never re-priced, never re-reads catalog") literally true: the order's `LineSnapshot`
   prices come from the exact `RecalculatedTotal` that was already confirmed and reserved, with zero
   re-derivation window. Option B would reopen the price-authority gap ADR-0003 item 1 and
   security-architecture §1.4 threat 2 were written to close. Synchronous commit/release (item 3)
   again avoids the same race ADR-0003 already ruled out once for the symmetric expiry path — one
   owner per reservation-state transition.
2. **Maintainability** — closes a self-contradiction in `domain-model.md` §4 rather than leaving two
   readings on record; a future reader of that table gets one consistent story.
3. **Testability** — `order`'s use cases depend only on interfaces it owns
   (`order.domain.port.out.*`), trivially fakeable, matching every other context.
4. **Performance** — in-process calls only; no alternative considered added network cost.
5. **Scalability** — not a driver; noted only to confirm none was invented.

Every choice here is the same shape ADR-0003 already established, applied to the next context —
not a new mechanism.

## Consequences

- **Positive**:
  - Order placement is provably faithful to what the customer confirmed and checkout reserved — no
    re-pricing window exists.
  - `CommitReservationUseCase`, previously unreachable dead code from a call-graph point of view,
    gets its intended caller.
  - `domain-model.md` no longer contradicts itself on how order's transitions reach inventory.
- **Negative / accepted trade-offs**:
  - `CheckoutAwaitingPaymentEvent` payload grows; any future consumer beyond `order` inherits the
    line-level detail whether or not it needs it (accepted — the event models what happened, not what
    one subscriber currently needs).
  - Order takes on two outbound port dependencies (`ReservationPort`, `ProductCatalogPort`) instead
    of the one a pure event-listener design would need — accepted per the correctness rationale above.
  - The retired-product-name fallback (item 1) is a narrow, accepted edge case: an order line's
    display name can read as a placeholder if the product was retired in the sub-second gap between
    checkout's commit and order's listener firing. Price and quantity are never affected.
- **Documentation corrections made alongside this ADR** (in scope for software-architect, who owns
  `domain-model.md`):
  - §2 aggregate table, `order` row: "References out" gains `ReservationId` (commit/release only,
    never displayed).
  - §3 class diagram: `Order` gains `ReservationId? reservation`.
  - §4 event catalog: `OrderPaidEvent` row's "checkout→inventory" corrected to "order→inventory";
    `OrderCancelledEvent` row's consumer changed from `inventory (restock), payment (refund)` to
    `— (audit; restock is a synchronous order→inventory port call, ADR-0004); future: payment
    (refund)`, matching `OrderFailedEvent`'s existing audit-note style.
- **Follow-up work created**:
  - When `payment` is implemented: it becomes `OrderCancelledEvent`'s first real subscriber
    (refund) — no new pattern, `@TransactionalEventListener(AFTER_COMMIT)` in
    `payment.application.listener`, per ADR-0003 item 5.
  - `inventory.application.usecase.ExpireReservationsUseCase` remains unwired, per ADR-0003 —
    unaffected by this ADR, restated here only so nobody assumes order's new `commit`/`release`
    calls change that status.

## Compliance

- Mechanically enforced by the existing `ArchitectureTest.contexts_communicate_only_through_ports_or_events`
  (rule 6, ADR-0001) — any `order` class importing `inventory.application.usecase.*`,
  `catalog.application.usecase.*`, or either context's `infrastructure`/`presentation` packages
  fails CI, exactly as it already does for checkout.
- Review checklist addition (software-architect gate): `MarkOrderPaidUseCase`, `CancelOrderUseCase`,
  and `FailOrderUseCase` must each call `ReservationPort` synchronously inside their own
  `@Transactional` method, and must not be paired with any inventory-side listener on
  `OrderPaidEvent`/`OrderCancelledEvent`/`OrderFailedEvent` — flag in review if one appears without
  superseding this ADR.
- `CheckoutAwaitingPaymentEvent.Line` field non-nullness and the event's non-empty `lines` invariant
  are constructor-enforced (compact constructor `Objects.requireNonNull`/`IllegalArgumentException`),
  not just documented.

## Quality Gates

- [x] Numbered sequentially in `docs/adr/`, never reuses a number
- [x] All affected agents can act on this decision without asking follow-ups
- [x] Superseded ADRs updated with a pointer to this one (none superseded — this extends ADR-0003's
      recipe to `order`, a context out of scope when ADR-0003 was written, and resolves an internal
      inconsistency in `domain-model.md` §4 that predates both ADRs)

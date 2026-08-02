# ADR-0003: Checkout crosses cart, pricing, and inventory only through new `application.port` façades; owns its own expiry sweep; hands off to order via a domain event

> Status: Accepted · Date: 2026-08-01 · Owner: software-architect

## Context

The `checkout` bounded context is about to be implemented (in-scope: `StartCheckoutUseCase`,
`ConfirmRecalculatedTotalUseCase`, `ExpirePaymentWindowUseCase`; `order`/`payment`/`promotions`
remain empty skeletons, out of scope). `docs/architecture/domain-model.md` §1/§2 already specifies
`CheckoutSession` and its need to read the customer's cart, get an authoritative price, and reserve
stock. `docs/architecture/backend-architecture.md` §1 sketches the intended outbound ports
illustratively; no code implements them.

`ArchitectureTest.contexts_communicate_only_through_ports_or_events` (rule 6, CI-enforced, ADR-0001
compliance rule 3) is unconditional: a class in context A may depend on context B only via classes
in `B.application.port` or `B.domain.event`. `B.application.usecase`, `.application.listener`,
`.infrastructure`, and `.presentation` are hard build failures regardless of intent.

Inspection of the already-committed `cart`, `pricing`, and `inventory` contexts found that none of
them expose an `application.port` façade today:

- `cart.application.usecase.ViewCartUseCase` and `cart.domain.port.out.CartRepository` are the only
  read paths to a customer's cart lines — both forbidden or private targets for checkout.
- `pricing.application.usecase.CalculateEffectivePriceUseCase` is a plain concrete class — a
  forbidden target.
- `inventory.application.usecase.ReserveStockUseCase` / `ReleaseReservationUseCase` /
  `ExpireReservationsUseCase` are plain concrete classes whose javadocs describe themselves as "an
  in-process port for the future checkout context to call directly" — which, read literally, would
  violate rule 6. This was an expectation left by earlier work, not a sanctioned exception.

The one precedent that already solves this correctly is `catalog.application.port.ProductLookupPort`
(`ProductSummary` projection, `findActiveById`), implemented by
`catalog.infrastructure.adapter.ProductLookupAdapter` (thin, direct against catalog's own
`ProductRepository`, `@Component`-scanned), and consumed by two independent contexts
(`cart.infrastructure.adapter.ProductCatalogAdapter`, `pricing.infrastructure.adapter.CatalogPriceAdapter`),
each adapting it to its **own**, differently-named `domain.port.out` interface
(`ProductCatalogPort` in both cases — same name, independently declared, decoupled from the
producer's name). This is the only existing multi-context crossing and it already establishes the
full shape of the answer; it had not yet been written down as a decision, and had not yet been
generalized to a rule other contexts must also follow.

Separately, `ADR-0001`'s "Follow-up work created" section flagged "Event publication pattern
decision ... formalize in a follow-up ADR when the first cross-context event is implemented" — no
such ADR was written even though `StockReservedEvent`/`StockReleasedEvent`/`UserAuthenticatedEvent`
already ship. Checkout is about to become the producer of the first domain event with a *known
future* cross-context consumer (`order`, not yet built), making this the forcing function to close
that gap too.

## Decision

We will:

1. **Add one `application.port` façade interface to each of `cart`, `pricing`, and `inventory`**,
   mirroring `catalog.application.port.ProductLookupPort` exactly (interface + nested projection
   records live together in `application.port`; implementation is a thin adapter in
   `infrastructure.adapter`, going directly against the context's own existing `domain.port.out`
   repository or reusing its existing use case where that use case's transaction boundary is
   itself part of the contract). These are additive files only — no existing domain/application
   code in cart, pricing, or inventory is modified.
   - `cart.application.port.CartReadPort`
   - `pricing.application.port.PriceCalculationPort`
   - `inventory.application.port.StockReservationPort`
2. **Checkout defines its own, independently-named `domain.port.out` interfaces** for each of the
   three (`CartLinesPort`, `PriceQuotePort`, `ReservationPort`), each implemented by a
   `checkout.infrastructure.adapter` class that calls the producer's façade and translates the
   producer's DTOs/exceptions into checkout's own — checkout's domain/application layers never
   import a type from `cart.*`, `pricing.*`, or `inventory.*` outside their `application.port`
   packages.
3. **Checkout owns its own payment-window expiry sweep** (`ExpirePaymentWindowUseCase`), which
   calls the new inventory façade's `release` operation synchronously when it expires a session.
   It does **not** rely on `inventory.application.usecase.ExpireReservationsUseCase`'s independent
   sweep — that use case stays implemented but un-scheduled/uninvoked, to avoid two competing
   sweepers racing to release the same reservation. This matches the codebase's own documented
   precedent (domain-model.md §4: `OrderFailedEvent` — "release already done synchronously by
   checkout").
4. **Checkout publishes a domain event, not a stub port, as the order hand-off seam.** A session
   reaching `AWAITING_PAYMENT` with a confirmed reservation publishes
   `checkout.domain.event.CheckoutAwaitingPaymentEvent`; a session the sweep expires publishes
   `checkout.domain.event.CheckoutSessionExpiredEvent`. Both follow the established publish
   pattern: constructor-injected `ApplicationEventPublisher`, called synchronously inside the same
   `@Transactional` use-case method that made the state change, no `@TransactionalEventListener`
   wrapping on the *publish* side (there is nothing to gate — the transaction is already open).
5. **Formalize the event pattern ADR-0001 deferred**: producers always publish synchronously as in
   (4). Consumers, once they exist, subscribe with `@TransactionalEventListener(phase = AFTER_COMMIT)`
   in their own `application.listener` package — the one existing precedent
   (`cart.application.listener.CartMergeEventListener` subscribing to auth's
   `UserAuthenticatedEvent`) already does this correctly; it is now the binding rule, not an
   accident of one implementation.

## Options Considered

| Option | Pros | Cons |
|--------|------|------|
| **A (chosen) — New `application.port` façade per provider context, mirroring `ProductLookupPort`** | Reuses a pattern already proven with two independent consumers; passes rule 6 in letter and spirit; each façade is a small, deliberate, versioned public contract instead of an accidental one; additive-only, zero risk to already-committed contexts | Three new files (plus three DTOs) to review; slight duplication of shape between a façade's own projection and the consumer's mirrored `domain.port.out` view (accepted — same trade-off `ProductCatalogPort` already makes) |
| B — Relax the ArchUnit rule to also allow `application.usecase` when the target class is `public` and stateless | Zero new files; checkout could call `ReserveStockUseCase` etc. directly today | Directly contradicts ADR-0001 compliance rule 3 and the CI gate; erodes the one mechanically-enforced boundary this codebase has; every future context gets the same temptation — the rule stops meaning anything |
| C — Give checkout direct repository/JPA access into cart/pricing/inventory's tables | Fewer moving parts for checkout's reads | Violates "one repository per aggregate root, nothing else" and "a context's tables are written/read only by its own adapters" (domain-model.md §2); breaks encapsulation the moment any of those three contexts changes its schema; not on the table given ADR-0001's model already forbids it |
| D — Do nothing now; let checkout call the use case classes and fix the ArchUnit failure later | Fastest to type | The CI gate is a required check — this option does not actually ship; "later" never arrives once the rule blocks every PR |

## Rationale

Ranked against the global-rules priority order:

1. **Correctness/security** — server-side price authority (security-architecture §1.2 threat 2)
   depends on checkout never touching pricing's or catalog's internals directly; a façade with its
   own DTOs is the only way to guarantee checkout cannot accidentally read or trust a stale
   client-influenced value. Synchronous inventory release on expiry (item 3) avoids a real
   correctness risk: two independent sweepers (inventory's own future scheduler and checkout's)
   both calling `release()` on the same reservation is a race the code already knows how to survive
   (`InvalidReservationStateException` swallowed in `ExpireReservationsUseCase`) but is unnecessary
   complexity to carry into v1 when one owner suffices.
2. **Maintainability** — this generalizes an already-legible pattern (`ProductLookupPort`) instead
   of inventing a second one; a developer who has read one façade has read them all.
3. **Testability** — checkout's use cases depend only on interfaces it owns
   (`checkout.domain.port.out.*`), trivially fakeable in unit tests, exactly like every other
   context's existing use cases.
4. **Performance** — in-process interface calls, no serialization; identical cost to option B, far
   cheaper than any network-based alternative that was never seriously considered.
5. **Scalability** — not a driver here; noted only to confirm no scalability rationale was invented
   to justify this (constraint: no designing for hypothetical scale).

Boring and consistent beats clever: option A is the same shape three more times, not a new
mechanism.

## Consequences

- **Positive**:
  - `cart`, `pricing`, `inventory` gain a documented, deliberate public surface instead of an
    implicit one; the next context that needs any of them (e.g. a future admin "cart contents"
    screen reading `CartReadPort`) has a seam to reuse instead of inventing a fourth way in.
  - Checkout's domain/application layers are fully insulated from the other three contexts'
    internal model changes.
  - `order`, when built, needs zero rework to checkout: it subscribes to
    `CheckoutAwaitingPaymentEvent`/`CheckoutSessionExpiredEvent` from its own
    `order.application.listener` package, following the `CartMergeEventListener` precedent.
- **Negative / accepted trade-offs**:
  - `inventory.application.usecase.ExpireReservationsUseCase` remains implemented but unwired —
    dead code from a call-graph point of view until/unless a future need (e.g. reservations created
    by a path other than checkout) justifies scheduling it. Documented here so nobody wires both
    it and checkout's sweep later without noticing the redundancy.
  - Three new façade interfaces plus three new checkout-side mirrors is more files than calling
    the use cases directly would have been. Accepted per ADR-0001's own stated trade-off
    ("ports + per-boundary mapping add ceremony to trivial flows").
  - **Price-mismatch is a 2xx result, not the registered `price-changed` problem type (security
    review addendum, 2026-08-01):** `StartCheckoutUseCase` returns a sealed `CheckoutOutcome`
    (`NeedsReconfirmation` / `Confirmed`) rather than throwing when the recalculated total differs
    from `expectedTotal`, even though api-guidelines §3.2 registers a `price-changed` problem type.
    This is a deliberate, documented deviation — full reasoning lives in
    `StartCheckoutUseCase`'s class javadoc ("Price mismatch — result, not exception") — recorded
    here only so it is not an *undocumented* gap against the problem-type registry.
- **Follow-up work created**:
  - When `order` is implemented: add `order.application.listener` subscribers for both checkout
    events; if order needs to report its id back onto the `CheckoutSession` it originated from,
    that requires a new `checkout.application.port` façade method at that time (not created now —
    YAGNI, no consumer exists yet).
  - When `payment`/`promotions` are implemented: `CouponRedemptionPort`/`PaymentGatewayPort`
    follow this same recipe (provider `application.port` façade + consumer `domain.port.out`
    mirror); no new pattern decision needed, this ADR already covers it.

## Compliance

- Mechanically enforced today by `ArchitectureTest.contexts_communicate_only_through_ports_or_events`
  (rule 6) — any checkout class importing `cart.application.usecase.*`,
  `pricing.application.usecase.*`, `inventory.application.usecase.*`, or any of the three contexts'
  `infrastructure`/`presentation` packages fails CI.
- Review checklist addition (software-architect gate): every new cross-context call in checkout
  names the `application.port` façade it goes through, in the PR description or class javadoc,
  matching this ADR's number.
- `inventory.application.usecase.ExpireReservationsUseCase` staying unscheduled is a review-time
  fact to check, not an automatable one — flag in review if a future PR adds a `@Scheduled` trigger
  for it without superseding this ADR.

## Quality Gates

- [x] Numbered sequentially in `docs/adr/`, never reuses a number
- [x] All affected agents can act on this decision without asking follow-ups
- [x] Superseded ADRs updated with a pointer to this one (none superseded; this fulfills the
      "event publication pattern" follow-up item ADR-0001 flagged, without contradicting it)

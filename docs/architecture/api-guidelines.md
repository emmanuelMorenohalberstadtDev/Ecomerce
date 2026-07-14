# API Guidelines

> Owner: backend-lead · Date: 2026-07-13 · Basis: `backend-architecture.md` (§5 error
> architecture, performance budgets), `security-architecture.md` (binding — referenced, never
> restated), `database-design.md` §8 (query budgets), skills `rest-api`, `validation`,
> `exception-handling`.
> Status: design-only. This is the contract rulebook: **every future endpoint must satisfy every
> rule here or cite the section it deviates from plus an ADR.** All endpoint paths shown in this
> document are illustrative examples, not committed endpoints — concrete endpoints are designed
> one at a time via the endpoint design template and get their auth decision table row (§7) before
> merge.

## 1. Resource Conventions

1. **Base path**: everything under `/api/v1`. No endpoint outside it (health/actuator excepted,
   per backend-architecture §6).
2. **Resources are plural nouns**; no verbs in paths, ever. *Example*: `POST /api/v1/orders`, not
   `/api/v1/createOrder` or `/api/v1/orders/place`.
3. **State transitions are sub-resources**, created with POST — the transition is the thing being
   created. *Example*: `POST /api/v1/orders/{orderId}/cancellation`. Tunnel-through-`PATCH
   status=X` is a review finding: the sub-resource form gives the transition its own request
   record, validation, and problem types.
4. **Identifiers in paths are UUIDs** (UUIDv7 per ADR-0002, accepted with conditions in
   security-architecture §6a — ids are identifiers, never capabilities). Path params are typed and
   validated; a malformed UUID is a 400, an unknown one a 404.
5. **JSON field naming**: `camelCase` everywhere, requests and responses. No snake_case leaks from
   the database layer — presentation records define the shape.
6. **Dates and times**: ISO-8601 UTC with `Z` suffix (`2026-07-13T18:00:00Z`). Never local time,
   never epoch numbers, never offset-bearing timestamps in responses.
7. **Money**: always the object `{"amount": "19.99", "currency": "ARS"}` — `amount` is a **string**
   (decimal, scale per currency) so no client ever passes it through a float; `currency` is
   ISO-4217 uppercase. Bare numeric prices anywhere in the contract are a defect.
8. **Nesting depth**: at most one level of sub-resource (`/carts/{id}/items/{itemId}`). Deeper
   relationships get their own top-level resource.
9. **GET is safe and body-less**; it never mutates, never carries a request body, and is the only
   verb eligible for caching headers (`ETag`/`Cache-Control` on catalog reads, coordinated with
   performance-engineer).

## 2. HTTP Verbs and Status Codes — Decision Table

| Situation | Status | Notes |
|---|---|---|
| Read succeeded | 200 | body = resource or page envelope (§4) |
| Resource created | 201 | `Location: /api/v1/<resource>/{id}` header mandatory; body = created representation |
| Mutation succeeded, useful body (e.g., add-to-cart → cart summary) | 200 | return what the client needs next; saves a round-trip |
| Mutation succeeded, nothing to return (DELETE, logout) | 204 | empty body |
| Async accepted (e.g., password-reset request) | 202 | body is generic per security-architecture §2.6 |
| Malformed request: bad JSON, wrong types, Bean Validation failure, unparseable param | 400 | field-error array (§3.3) |
| Not authenticated / token missing, expired, invalid | 401 | problem types `token-expired`, `token-invalid`, `invalid-credentials` (§3.2) |
| Authenticated but the operation is not for this role (e.g., ADMIN hitting checkout) | 403 | roles only — never ownership (security-architecture §3.3) |
| Resource does not exist **or is owned by someone else** | 404 | ownership failures are 404, never 403 — security-architecture §3.3 |
| State conflict: version clash, duplicate transition, stock/coupon race, already-processed key with different body | 409 | maps `ConflictException` (backend-architecture §5) |
| Well-formed but semantically rejected by a business rule (order not cancellable, coupon not applicable) | 422 | maps `BusinessRuleException` |
| Rate limit tripped | 429 | `Retry-After` header; security-architecture §2.2 |
| Unexpected failure | 500 | generic body + correlation id; leaks nothing (backend-architecture §5) |

Hard rules:

- **Never 200 with an error payload.** Success status means success; frontend branches on status
  + problem `type`, nothing else.
- **Never 500 for an expected business outcome** — a declined payment, an empty cart, an expired
  window all have mapped statuses.
- **400 vs 422**: 400 = "I could not accept the shape of this request" (boundary validation);
  422 = "I understood it and the domain says no". 409 = "the world changed under you — re-read
  and retry".
- **401 vs 403 vs 404**: 401 = who are you; 403 = wrong role; 404 = wrong owner or nonexistent
  (indistinguishable by design).
- Verb semantics: GET (safe), POST (create/process, non-idempotent unless §5 applies), PUT (full
  replace, idempotent), PATCH (partial update), DELETE (idempotent — repeat delete of a gone
  resource is 404, which is acceptable idempotent behavior).
- **Auth endpoints override this table where anti-enumeration requires it** (registration
  conflict, reset flows): security-architecture §2.1/§2.6 wins; the general table is not a license
  to emit a 409 that becomes an enumeration oracle.

## 3. Error Contract — RFC 9457 Problem Details

### 3.1 Shape

Every non-2xx response is `application/problem+json` produced by the single
`ApiExceptionHandler` (backend-architecture §5). Fields:

| Field | Rule |
|---|---|
| `type` | `https://api.ecommerce.dev/problems/<slug>` — the stable machine contract |
| `title` | short human phrase, stable per type |
| `status` | duplicates the HTTP status |
| `detail` | human-readable, safe (no class names, SQL, ids of other users) |
| `instance` | the request path |
| extensions | structured, per-type documented fields (e.g., `availableQuantity` on insufficient-stock) |
| `errors` | validation 400s only — see §3.3 |

Frontend keys UX off `type`; tests assert `type` + `status`, never message text.

### 3.2 Type slug registry

The registry is append-only and lives in this section; adding a slug is part of the endpoint's
design PR. Initial registry:

| Slug | Status | Origin |
|---|---|---|
| `token-expired` | 401 | access JWT past `exp` — security-architecture §6b |
| `token-invalid` | 401 | signature/issuer/alg/shape failure — security-architecture §6b |
| `invalid-credentials` | 401 | login failure, generic per security-architecture §2.2 |
| `too-many-attempts` | 429 | rate limit / lockout — security-architecture §2.2, §5.2 |
| `invalid-or-expired-token` | 400 | reset-token failures, one generic type — security-architecture §2.6 |
| `validation-failed` | 400 | Bean Validation failure, carries `errors` array (§3.3) |
| `insufficient-stock` | 409 | reservation/decrement failed (inventory) |
| `cart-not-active` | 422 | operation on a non-ACTIVE cart |
| `order-not-cancellable` | 422 | lifecycle state forbids cancellation |
| `coupon-not-applicable` | 422 | generic — deliberately covers unknown/expired/capped codes (security-architecture §1.4 case 3) |
| `price-changed` | 409 | checkout re-confirmation: server total differs from the one the client confirmed |
| `not-found` | 404 | default for missing/unowned resources |
| `conflict` | 409 | default for unnamed `ConflictException` leaves — naming a specific slug is preferred |
| `internal-error` | 500 | generic, correlation id in extension `correlationId` |

One domain exception → one slug; a slug never changes status code once published.

### 3.3 Validation errors (400)

`validation-failed` problems carry an `errors` array collecting **all** field failures (never
first-only): `{"field": "lines[2].quantity", "code": "order.line.quantity.max", "message": "…"}`.
`code` is the stable contract for frontend form mapping; `message` is human-readable and may
change freely.

## 4. Pagination, Sorting, Filtering

1. **Every collection endpoint is paginated.** No exceptions, no "there won't be many".
2. **Parameters**: `page` (0-based, default 0), `size` (default 20, **hard cap 100 — values above
   are clamped to 100**, negative/zero → 400), `sort=field,direction` (repeatable; direction
   `asc`/`desc`, default per endpoint documented in its design).
   The clamp on `size` is the single sanctioned exception to the "reject, don't clamp" validation
   rule — it is a documented load-protection behavior from the performance budgets
   (backend-architecture), not silent data correction.
3. **Response envelope** — identical on every list:

   ```json
   { "content": [ … ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
   ```

4. **Sort fields are allowlisted per endpoint** (declared in the endpoint design; enforced in the
   use case). A sort field outside the allowlist → 400. Raw sort strings never reach the
   persistence layer unmapped (injection surface — security-architecture §4/A03).
5. **Filters are documented query params with allowlisted names**; unknown params are ignored,
   invalid values for known params → 400.
6. **Deep pages**: offset pagination is the v1 contract, but database-design §8 requires keyset
   pagination beyond page depth ~50. Endpoints expected to be browsed deep (order history, admin
   lists) must state in their design how they honor that budget — either capping navigable depth
   or adding a keyset variant (cursor param, additive change). Do not wait for the slow query to
   ship first.

## 5. Idempotency

1. **`Idempotency-Key` header is required** on mutations with money or stock consequences —
   at minimum order placement and payment submission (security-architecture §1.4 case 6 mandates
   it; this section defines the contract semantics). Missing key on such an endpoint → 400.
2. **Key format**: client-generated, 1–255 chars, opaque to the server. One key = one logical
   operation attempt.
3. **Replay semantics**: same key + same request → the **original result** is returned (original
   status and body; a replayed 201 returns the same body with 200 or 201 — the endpoint design
   fixes which, and keeps it stable). Same key + **different body** → 409 (`conflict`), because
   the client is confused and silently honoring either request is dangerous.
4. **Concurrent duplicates** (same key, in flight) must not both execute; the second waits or
   receives 409 — the endpoint design states which.
5. **Scope**: keys are scoped per authenticated principal and per endpoint; they are not bearer
   secrets but are generated with full entropy when secrecy matters (security-architecture §6a).
6. **Storage, retention window, and cleanup are implementation details deferred to the backend
   design of the first consuming feature (checkout)** — the contract above is what frontend and
   tests rely on regardless of storage choice.

## 6. Versioning and Compatibility

1. **Additive = in-place**: new optional request fields, new response fields, new endpoints, new
   enum *outputs* the frontend is told to tolerate — allowed within `/api/v1`.
2. **Breaking = new version**: renaming/removing fields, changing types or semantics, tightening
   validation on existing fields, changing a status code or problem `type` — requires `/api/v2`
   for the affected surface plus a deprecation window during which v1 keeps working; window length
   agreed with frontend-lead through the orchestrator.
3. **Contract-first workflow**: the OpenAPI spec is the interface. Every endpoint PR updates the
   spec **in the same change**; frontend consumes the spec and never guesses; a divergence between
   spec and behavior is a defect on the backend side regardless of which one is "right".
4. Contract changes are reviewed as breaking-by-default: the PR must argue additivity, not assume
   it.

## 7. Auth-Related Endpoint Rules

Design authority is `security-architecture.md`; this section only fixes contract shapes and
pointers. Deviations go back to security-engineer, not into code.

1. **Token transport**: login/refresh responses carry the access token **in the JSON body**; the
   refresh token travels **only** as the httpOnly cookie — never in a body, URL, or non-cookie
   header (security-architecture §2.3, §2.5). No endpoint ever echoes a token back.
2. **Auth endpoint response shapes** follow the flows in security-architecture §2: login success
   200 body `{accessToken, expiresIn}` + `Set-Cookie`; refresh 200 same shape; logout 204 +
   clearing cookie; password-reset request always 202 (§2.6); registration responses follow the
   anti-enumeration pattern of §2.1. Problem types for failures come from the registry (§3.2).
3. **Every new endpoint ships its auth decision table row** (GUEST/CUSTOMER/ADMIN × outcome) as an
   expansion of the seed table in security-architecture §3.4 — no row, no merge.
4. **No caller identity in request bodies**: `customerId`, `userId`, "on behalf of" fields in
   requests are review-blocking; the principal comes from the token via `CurrentUserPort`
   (security-architecture §3.2).
5. **Ownership failures are 404** (§2 table; semantics owned by security-architecture §3.3).
6. Refresh/logout carry the Origin-check requirement of security-architecture §2.5; rate-limited
   endpoints (auth, coupon-apply) return 429 `too-many-attempts` per §2.2/§5.2.

## 8. Request Validation Rules

1. **Bean Validation on every request field** — an unconstrained field is a review finding.
   Controllers use `@Valid` on bodies and `@Validated` for path/query params (skill `validation`).
2. **`@Size` bounds on every String and every collection.** Unbounded input is a DoS vector;
   lists are validated deeply (`List<@Valid X>` with `@Size(max = …)`).
3. **Boundary vs domain**: annotations check shape (presence, format, range) → 400; business rules
   live in domain invariants → 409/422 (backend-architecture §5). Rules needing repository access
   are never annotations.
4. **Normalization before validation** in DTO compact constructors: trim strings, lowercase
   emails, collapse case on codes where the domain defines them case-insensitive. Out-of-range
   values are rejected, not corrected (sole exception: §4.2 page-size clamp).
5. **The server recalculates all money.** Client-sent prices, totals, and discount amounts are
   ignored by design — the request may carry the amount the user *saw* only for re-confirmation
   comparison (→ `price-changed` 409), never as an input to arithmetic
   (security-architecture §1.4 case 2).
6. Repeated formats get one custom constraint (`@ValidSku`-style), one validator, reused; custom
   validators are side-effect-free.
7. Request body size is limited at nginx **and** in the app (security-architecture §5.2); the app
   limit produces a 400 problem, not a connection reset.

## Compliance

- Every endpoint design (template + OpenAPI diff) is checked against sections 1–8 before
  implementation starts; reviewer treats a violation without an ADR citation as a defect.
- test-engineer derives the per-endpoint contract tests from §2 (status semantics), §3
  (problem `type` + `status`), §4 (envelope + cap), §5 (replay), and the §7.3 auth row.

---
name: rest-api
description: REST contract rules for the ecommerce API — resource naming, verbs, status codes, pagination, versioning, idempotency, and response shapes.
---

# REST API

## Purpose

One predictable API contract: any consumer can guess how an endpoint behaves from the rules, and every endpoint honors the guess.

## When to Use

Designing any endpoint (`templates/api.md`), reviewing controllers, resolving contract questions.

## Rules

1. **Resources are plural nouns**; actions are expressed by verb+resource, not RPC paths: `POST /api/v1/orders`, not `/api/v1/createOrder`. State transitions as sub-resources or status updates: `POST /api/v1/orders/{id}/cancellation`.
2. **Verbs**: GET (safe, cacheable, no body), POST (create/process), PUT (full replace, idempotent), PATCH (partial), DELETE (idempotent). GET never mutates — no exceptions.
3. **Status codes**:
   - 200 OK, 201 Created (+ `Location` header), 204 No Content (DELETE, empty PUT results)
   - 400 malformed/validation, 401 unauthenticated, 403 forbidden, 404 not found (also for others' resources — don't leak existence), 409 state conflict (stock, duplicate), 422 semantically invalid
   - Never 200 with an error body; never 500 for expected business failures.
4. **Errors**: RFC 9457 `application/problem+json` everywhere (see skill `exception-handling`).
5. **Pagination mandatory** on every collection: `?page=0&size=20&sort=createdAt,desc`; `size` capped server-side (max 100). Response: `{content, page, size, totalElements, totalPages}`.
6. **Versioning**: URI prefix `/api/v1`. Breaking changes → v2 + deprecation window; additive changes (new optional fields) are non-breaking and allowed in-place.
7. **Idempotency**: unsafe operations with money/stock consequences (place order, pay) accept an `Idempotency-Key` header; replays return the original result.
8. **Field naming**: `camelCase` JSON; dates ISO-8601 UTC (`2026-07-13T18:00:00Z`); money as `{"amount": "19.99", "currency": "ARS"}` — amount as string to avoid float clients.

## Examples

```
POST /api/v1/carts/{cartId}/items          → 201, cart summary
GET  /api/v1/products?page=0&size=20&category=shoes&sort=price,asc
POST /api/v1/orders  (Idempotency-Key: k)  → 201 + Location: /api/v1/orders/{id}
                                            → replay with same key: 200, same body
GET  /api/v1/orders/{id}  (other user's)   → 404 (not 403)
```

## Best Practices

- Filtering via query params with documented allowlist; unknown params ignored, invalid values → 400.
- Return the representation the client needs next (add-to-cart returns the cart summary, saving a round-trip).
- `ETag`/`Cache-Control` on catalog GETs (coordinate with performance-engineer).
- Contract-first: `templates/api.md` + OpenAPI before implementation; frontend consumes the spec, never guesses.

## Common Mistakes

- Verbs in paths, `GET /orders/delete/{id}`, or tunnel-everything-through-POST.
- Exposing DB ids sequence patterns where enumeration matters — prefer UUIDs on user-visible resources (per ADR).
- Collection endpoints without pagination "because there won't be many" — there will.
- Different error shapes per controller.
- Breaking the contract silently (renaming a field) — that's a MAJOR version event.

## References

- RFC 9110 (HTTP semantics), RFC 9457 (problem details)
- See skills `exception-handling`, `validation`, `spring-boot`

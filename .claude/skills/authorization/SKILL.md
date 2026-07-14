---
name: authorization
description: Authorization model — roles, the auth decision table, ownership enforcement patterns, method security placement, and admin-surface rules.
---

# Authorization

## Purpose

Every operation answers "who may do this, to which resource?" from one written table — enforced in code at the right layer, proven by tests, with ownership (not just role) as the core check.

## When to Use

Adding/changing any endpoint or use case, designing admin features, reviewing access control (Gate 4's A01 — the top ecommerce vulnerability).

## Rules

1. **Roles are few and flat (v1)**: `GUEST` (anonymous), `CUSTOMER`, `ADMIN`. Resist role proliferation; capability differences within a role are business rules, not new roles. Hierarchies/permissions matrices arrive only via ADR when a real need exists (YAGNI).
2. **The auth decision table is the law** (security-engineer owns it; lives with the auth design doc): every endpoint → allowed roles + ownership rule + notes. An endpoint absent from the table fails review — no implicit access decisions.
3. **Two enforcement layers, each doing its job**:
   - *Coarse (route)*: `SecurityFilterChain` matchers — public catalog reads, `ADMIN`-only prefixes (skill `spring-security`).
   - *Fine (operation)*: `@PreAuthorize` on use cases + **ownership checks inside the use case/domain** (`order.belongsTo(customerId)`); the repository query itself scopes by owner where natural (`findByIdAndCustomerId`) — the strongest pattern: unowned data is unfetchable, not just unfiltered.
4. **Ownership failures return 404, not 403** (don't confirm the resource exists — skill `owasp` A01). 403 is reserved for "you're authenticated, the *operation* is not for your role."
5. **The frontend mirrors but never enforces**: route guards and hidden buttons are UX (guards check the session, API responses drive what's shown — e.g., the `canCancel` flag from skill `order-lifecycle`); every real decision happens server-side. Anything reachable by editing client state must be safe to reach.
6. **Admin surfaces are a separate risk class**: `/api/v1/admin/**` prefix, `ADMIN` at both layers, every mutation audit-logged (who, what, when, before/after), no admin endpoints "temporarily" open in dev profiles.
7. **Every table row gets tests** (API tier, from `templates/test-plan.md` mandatory categories): anonymous → 401, wrong role → 403, wrong owner → 404, right principal → 2xx. The table without the tests is documentation; with them it's a system.

## Examples

```
Auth decision table (excerpt — the artifact itself):
| Endpoint                          | GUEST | CUSTOMER      | ADMIN | Ownership rule            |
|-----------------------------------|-------|---------------|-------|---------------------------|
| GET  /products, /products/{id}    | ✓     | ✓             | ✓     | —                         |
| POST /carts/{id}/items            | ✓ (own cart token) | ✓ (own cart) | —  | cart belongs to identity |
| GET  /orders/{id}                 | —     | ✓ own → else 404 | ✓  | order.customerId == sub   |
| POST /orders/{id}/cancellation    | —     | ✓ own + state-cancellable | ✓ | ownership + lifecycle |
| PUT  /admin/products/{id}         | —     | —             | ✓     | audit-logged              |
```

```java
// ownership by query scoping — unfetchable beats filtered
public OrderDetail execute(OrderId id, CustomerId requester) {
    var order = orders.findByIdAndCustomerId(id, requester)
        .orElseThrow(() -> new OrderNotFoundException(id));   // 404 either way
    return OrderDetail.from(order);
}
```

## Best Practices

- Put `@PreAuthorize` on the application layer (use cases), not controllers — the rule guards the *operation* no matter what driver invokes it (see skill `hexagonal`).
- Derive the principal from `SecurityContext` in one adapter (`CurrentUserPort`) — use cases receive a typed `CustomerId`, never parse tokens themselves.
- Diff the decision table in PRs like code — a changed row is a security decision needing security-engineer's eyes.
- List endpoints programmatically (mapping introspection test) and assert each appears in the table — drift detection automated.

## Common Mistakes

- Checking role and forgetting ownership (`CUSTOMER` can read `/orders/{any-id}` — the #1 real-world ecommerce vuln, IDOR).
- Filtering owned items in memory after fetching everything (`findAll().stream().filter(...)` — one missed path leaks the store).
- Authorization logic duplicated in controller AND use case, drifting until the controller wins with the older rule.
- Trusting client-supplied ids for identity (`customerId` in the request body instead of from the token — the token *is* the identity).
- Admin checks only at the route prefix, then an admin use case reused by a customer-facing controller without its guard.

## References

- OWASP Authorization Cheat Sheet; OWASP Top 10 A01
- See skills `spring-security`, `owasp`, `hexagonal`, `order-lifecycle`, `rest-api`

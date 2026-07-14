---
name: owasp
description: OWASP Top 10 mapped to this stack — the concrete vulnerability checklist security-engineer runs against every gated diff, with stack-specific mitigations.
---

# OWASP

## Purpose

The OWASP Top 10 (2021) translated into checkable rules for Spring Boot 3 + Angular 21 + PostgreSQL — the floor every diff must clear at Gate 4.

## When to Use

Security reviews (Gate 4), threat modeling, designing any feature touching input, auth, money, or PII.

## Rules — the Top 10 as this project's checklist

1. **A01 Broken Access Control** (the #1 ecommerce killer): every endpoint in the auth decision table; ownership checks on user-scoped resources (`order.belongsTo(principal)`) — role checks alone never suffice; other users' resources return 404; no IDOR via sequential ids on user-visible resources (per ADR); CORS explicit (skill `spring-security`).
2. **A02 Cryptographic Failures**: BCrypt for passwords; TLS in transit; JWT secrets ≥ 256-bit from env; no PII in JWT payloads or URLs; no custom crypto ever.
3. **A03 Injection**: JPA parameter binding always — string-concatenated JPQL/SQL is an automatic blocker; dynamic sort/filter fields validated against allowlists (`sort=createdAt` → enum, never spliced into the query); no OS command execution from request data.
4. **A04 Insecure Design**: server-side price authority (skill `pricing`); idempotency on money operations; rate limiting on auth + coupon endpoints; stock/redemption caps atomic (skills `inventory`/`promotions`); business-logic abuse cases in threat models (negative quantities, coupon brute-force).
5. **A05 Security Misconfiguration**: actuator exposure minimal; stack traces never in responses (skill `exception-handling`); security headers via nginx (skill `nginx`); `server_tokens off`; default credentials impossible (no seeded admin/admin).
6. **A06 Vulnerable Components**: dependency audit in CI (skill `github-actions`); criticals block merge; every new dependency architect-approved (global rule); pinned versions everywhere.
7. **A07 Authentication Failures**: rate-limited login; generic auth error messages (no user-exists oracle — same response, same *timing class*, for wrong-user and wrong-password); refresh rotation + reuse detection (skill `jwt`); password policy per security-engineer.
8. **A08 Integrity Failures**: pinned GitHub Actions; lockfiles committed; images pinned/digested (skills `github-actions`/`docker`); no deserialization of untrusted native objects (JSON via Jackson to typed records only).
9. **A09 Logging & Monitoring Failures**: auth events (login success/failure, token reuse) logged with correlation ids; no secrets/PII/tokens in logs (log-scrubbing per skill `secure-coding`); Gate 4 checks new log lines for leakage.
10. **A10 SSRF**: any server-side fetch of a URL influenced by user input (webhooks, image imports) → allowlist of hosts, no redirects followed blindly, no internal network reachability.

## Examples

Gate 4 review pass, in order:

```
□ Diff touches an endpoint? → auth decision table row exists, matches, has an authZ test
□ New input? → validated (skill `validation`), bounded (@Size on lists/strings)
□ New query with dynamic parts? → parameters bound, sort/filter allowlisted
□ New log lines? → no tokens, passwords, PII, full card data (ever — PAN never touches our systems)
□ New dependency/action/image? → approved, pinned, audited
□ Money/state change? → server-computed, idempotent, atomic under concurrency
□ New error path? → problem detail leaks no internals, 404 for others' resources
```

## Best Practices

- Review the endpoint's *full* context, not just the diff hunk (a new field on an old endpoint inherits its authZ gaps).
- Abuse cases into every sensitive threat model: "how would I get this for free / drain this cap / enumerate this?"
- Keep the checklist result in the PR as Gate 4 evidence (template checkbox is the pointer, the report is the artifact).

## Common Mistakes

- Treating the Top 10 as a one-time audit instead of a per-diff gate.
- Passing A03 by "we use JPA" while a `Sort.by(request.getParameter(...))` slips through.
- 403 responses on other users' orders (confirms existence — A01 says 404).
- Logging the full request body "for debugging" on auth endpoints.
- Blocking on hypothetical A10 for features with zero server-side fetching — findings map to actual attack surface (severity honesty, security-engineer's rule).

## References

- owasp.org/Top10 (2021); OWASP ASVS for depth; OWASP Cheat Sheet Series
- See skills `authentication`, `authorization`, `secure-coding`, `jwt`, `spring-security`

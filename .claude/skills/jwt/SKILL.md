---
name: jwt
description: JWT access/refresh token design — claims, lifetimes, rotation, revocation, storage, and validation rules for the ecommerce API.
---

# JWT

## Purpose

Design and validate JSON Web Tokens so authentication is stateless, short-lived, and recoverable from compromise.

## When to Use

Designing or implementing token issuance, validation, refresh, logout; deciding claims and storage.

## Rules

1. **Two tokens**: access token (short: 10–15 min) for API calls; refresh token (days, per security-engineer) only to mint new access tokens via `/api/v1/auth/refresh`.
2. **Claims minimal**: `sub` (user id), `roles`, `iat`, `exp`, `jti`. No PII (email/name) in the payload — it's base64, not encrypted; anyone can read it.
3. **Refresh rotation**: every refresh issues a new refresh token and invalidates the old (`jti` stored server-side). A reused rotated token = theft signal → revoke the whole family.
4. **Revocation**: refresh tokens are revocable server-side (DB table: jti, user, expiry, revoked). Access tokens are not tracked — their short life *is* the mitigation; logout revokes the refresh family.
5. **Validation on every request**: signature, `exp`, expected issuer/audience, algorithm pinned (HS256 or RS256 per ADR) — never accept the header's alg claim blindly; reject `none`.
6. **Storage (frontend)**: refresh token in an httpOnly, Secure, SameSite cookie scoped to the refresh path; access token in memory only. localStorage is rejected (XSS-readable).
7. Signing secret/keys from environment, rotated per security-engineer's policy; minimum 256-bit for HMAC.

## Examples

```json
// access token payload — nothing sensitive
{ "sub": "u_8f3c", "roles": ["CUSTOMER"], "iat": 1752300000, "exp": 1752300900, "jti": "a1b2" }
```

Refresh flow: `POST /auth/refresh` (cookie) → verify signature+exp → check jti not revoked → rotate (revoke old jti, issue new pair) → return access token in body, new refresh in cookie.

## Best Practices

- Clock skew tolerance small and explicit (≤ 60s) in validation.
- Include token version/user-credentials-changed check if password reset must kill sessions (bump a per-user token version claim).
- Distinct problem-detail types: `token-expired` vs `token-invalid` — the frontend auto-refreshes only on expired.
- Test the ugly paths: expired, tampered signature, reused rotated refresh, revoked family.

## Common Mistakes

- Long-lived access tokens "so users stay logged in" — that's the refresh token's job.
- Putting roles in the token and never re-checking on sensitive operations — an admin-demoted user keeps admin for the token's life; keep access tokens short and re-verify for critical actions.
- Building session logout by blacklisting access tokens in a table consulted on *every* request — you've rebuilt stateful sessions with extra steps; revoke at the refresh layer.
- Trusting `alg` from the token header (algorithm confusion attacks).

## References

- RFC 7519 (JWT), RFC 6749 (OAuth2 concepts), OWASP JWT Cheat Sheet
- See skills `spring-security`, `authentication`, `secure-coding`

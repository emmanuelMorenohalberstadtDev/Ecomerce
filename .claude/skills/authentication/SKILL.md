---
name: authentication
description: Authentication flows for the store — registration, login, token refresh, logout, password reset, guest identity, and anti-enumeration rules.
---

# Authentication

## Purpose

Prove who the user is without leaking who your users are — every auth flow designed end-to-end (happy, sad, and abuse paths) before implementation. (Token mechanics: skill `jwt`; Spring wiring: skill `spring-security`.)

## When to Use

Designing/implementing/reviewing registration, login, refresh, logout, password reset, or guest→customer identity flows.

## Rules

1. **Registration**: email + strong password (length ≥ 12 as primary rule; check against known-breached lists if feasible — composition rules over length are outdated); email uniqueness enforced at DB (skill `postgresql`) but reported without an oracle (see rule 4); verify email before enabling sensitive actions per product-owner policy.
2. **Login**: rate-limited per account *and* per IP (edge: skill `nginx`; app-level counter for lockout/backoff per security-engineer's design); success → token pair (skill `jwt`); failure → generic message.
3. **Logout** = revoke the refresh-token family server-side + clear the cookie; the access token simply expires (short TTL is the design — no access-token blacklist, see skill `jwt`).
4. **No user-enumeration oracles, anywhere**:
   - Login failure: "Invalid credentials" whether the user exists or not — and hash a dummy password when the user doesn't exist so timing doesn't tell.
   - Password reset: "If that account exists, an email was sent" — always, both branches.
   - Registration conflict: generic "unable to register with this email" + the real notice via email to the existing owner.
5. **Password reset**: single-use, expiring (≤ 1h), random token (≥ 128-bit, stored hashed like a password); consuming it revokes all refresh families (a reset means possible compromise); old token invalidated when a new one is requested.
6. **Password change** (logged-in): requires current password; revokes all *other* refresh families; user notified by email.
7. **Guest identity is real identity**: the guest cart token (skill `shopping-cart`) is unguessable, httpOnly, and merges into the account at login — auth flows must define the guest→customer transition explicitly, not as an afterthought.
8. **Auth events are logged** (success, failure, lockout, reset requested/consumed, refresh-reuse detected) with correlation ids — but never the credentials themselves (skill `secure-coding`).

## Examples

```
Login endpoint contract (the anti-oracle discipline, concretely):
  POST /api/v1/auth/login {email, password}
  → 200 {accessToken} + Set-Cookie refresh   (valid)
  → 401 problem type "invalid-credentials"   (wrong password OR unknown user — identical body)
  → 429 problem type "too-many-attempts"     (rate limit, Retry-After header)
  Unknown-user path still runs BCrypt against a fixed dummy hash (constant-time discipline).
```

```
Password reset flow (all branches defined):
  request → always 202 "if exists, sent" → email w/ token link (15–60 min TTL)
  consume → validate hash+expiry+unused → set password → mark used → revoke all sessions → 200
  invalid/expired/reused token → 400 "invalid-or-expired-token" (no distinction — no oracle)
```

## Best Practices

- Model each flow as a sequence diagram in the auth design doc (security-engineer's deliverable) before backend-lead writes a line — flows have more edges than endpoints.
- Test the oracle-resistance explicitly (API tier): assert identical status + body shape for exists/not-exists branches.
- Session inventory ("active sessions" from refresh families) is cheap once families exist — plan the table for it even if the UI ships later.
- Keep MFA out of v1 unless product-owner requires it, but don't design token/reset flows that would preclude adding TOTP later (YAGNI with an escape hatch).

## Common Mistakes

- "Email already registered" on the register form (the classic enumeration gift) — fix the UX with the email-notice pattern.
- Reset tokens stored in plaintext (a DB read becomes account takeover for every user).
- Logout implemented client-side only (deleting the cookie while the family stays valid server-side).
- Password change that doesn't revoke other sessions (the attacker you're changing the password *because of* stays logged in).
- Rate limiting per-IP only (attacker rotates IPs; victims behind CGNAT get locked out) — layer per-account + per-IP.

## References

- OWASP Authentication & Forgot Password Cheat Sheets; NIST SP 800-63B (password guidance)
- See skills `jwt`, `spring-security`, `owasp`, `secure-coding`, `shopping-cart`

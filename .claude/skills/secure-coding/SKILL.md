---
name: secure-coding
description: Day-to-day secure coding habits — input distrust, output encoding, secrets handling, safe logging, error hygiene, and frontend XSS discipline.
---

# Secure Coding

## Purpose

The habits that keep individual lines of code from becoming findings — what every implementer applies constantly, between the big design reviews. (Checklist framing: skill `owasp`; auth specifics: skills `authentication`/`authorization`.)

## When to Use

Writing any code that touches input, output, secrets, logs, files, or errors — i.e., most code. Loaded by default for backend-lead and frontend-lead work.

## Rules

1. **All input is hostile until validated** — request bodies, query params, path variables, headers, cookies, file names, webhook payloads. Validate shape at the boundary (skill `validation`), bound every string and collection (`@Size` — unbounded input is a DoS), allowlist over blocklist always (sort fields, file extensions, redirect targets).
2. **Identity comes from the token, never the payload**: any `userId`/`customerId`/`email` field in a request body that identifies *the caller* is a finding — derive from `SecurityContext` (skill `authorization`).
3. **Secrets discipline**:
   - From environment only; no defaults in yml; `.env` gitignored with `.env.example` maintained (skill `docker-compose`).
   - Never in logs, exceptions, URLs, or error responses; never committed "just locally" (git remembers forever — a committed secret is a *rotated* secret, immediately).
   - Compare secrets/tokens with constant-time equality (`MessageDigest.isEqual`), store reset/API tokens hashed (skill `authentication`).
4. **Safe logging**: log events and identifiers, not payloads — no passwords, tokens, session cookies, full PANs (never even receive PANs — gateway tokenization only), or unmasked PII. Mask emails/addresses where they must appear (`e***@gmail.com`). User-controlled strings in logs are encoded/truncated (log-injection: CRLF-strip anything interpolated).
5. **Error hygiene**: internals stay in logs, correlation id + generic problem detail goes out (skill `exception-handling`); catch specific exceptions; never leak `SQLException` messages, file paths, or class names to responses.
6. **Frontend XSS discipline (Angular)**: interpolation auto-escapes — keep it that way. `[innerHTML]` requires sanitization and a review-visible justification; `bypassSecurityTrust*` is a security-engineer sign-off, not a convenience; never build templates/HTML from user strings; URLs from user data validated before binding to `href`/`src` (block `javascript:`).
7. **Files, if handled**: validate type by content (magic bytes) not extension; random server-side names (user filename is display metadata only); store outside the web root / in object storage; size limits at nginx AND app (skill `nginx`).
8. **Randomness**: `SecureRandom`/`UUID.randomUUID()` for anything security-relevant (tokens, cart tokens, idempotency keys) — `Math.random()`/`ThreadLocalRandom` are for shuffling product carousels, nothing more.

## Examples

```java
// allowlist for dynamic sort — the A03 habit
private static final Set<String> SORTABLE = Set.of("createdAt", "price", "name");
Sort toSort(String field, String dir) {
    if (!SORTABLE.contains(field)) throw new InvalidSortFieldException(field);
    return Sort.by(Sort.Direction.fromString(dir), field);   // dir validated by fromString
}
```

```java
// safe log line: event + ids + correlation, nothing juicy
log.warn("login.failed accountRef={} attempt={} correlationId={}",
         maskedRef(email), attemptCount, MDC.get("correlationId"));
// NOT: log.warn("login failed for {} with password {}", email, password)
```

## Best Practices

- Grep your own diff before PR: `password`, `secret`, `token`, `printStackTrace`, `innerHTML`, `bypassSecurityTrust`, `Math.random` — thirty seconds that prevents most Gate 4 findings.
- Prefer types that make misuse impossible: a `HashedToken` type can't be logged as its raw value; `Money` can't be a `double` (skill `pricing`).
- Fail closed: on doubt (invalid state, unexpected null, unmatched case) reject the request — never proceed with a best guess on a security-relevant path.
- Keep third-party webhook handlers signature-verified and idempotent (payment gateways document this — implement it, test it).

## Common Mistakes

- Blocklist validation ("strip `<script>`") — attackers know more encodings than you; allowlist the good.
- Logging the full exception *and* re-throwing (double logs), or logging request bodies at DEBUG that ships to prod at DEBUG.
- Secrets in docker-compose files "because it's just dev" — dev files get copied to prod more often than anyone admits.
- Building SQL/JPQL/HTML/shell strings with `+` and user data — parameterize, bind, encode; no exceptions.
- Verifying a webhook by "it came to our secret URL" (URLs leak — verify signatures).

## References

- OWASP Cheat Sheet Series (Input Validation, Logging, XSS Prevention, File Upload); Angular security guide (angular.dev/best-practices/security)
- See skills `owasp`, `validation`, `exception-handling`, `authentication`, `authorization`

---
description: Security design, threat model, or Gate 4 review (security-engineer agent)
argument-hint: "[what to secure or review: feature, diff, auth flow]"
---

Invoke the **security-engineer** agent for: $ARGUMENTS

Depending on the request, it delivers:
- **Design** (auth flows, tokens, authorization model) → design doc + auth decision table updates, using skills `authentication`, `authorization`, `jwt`, `spring-security`.
- **Threat model** (feature touching money/PII/auth) → assets, actors, attack surfaces, mitigations — before implementation starts.
- **Review** (Gate 4 on a diff) → run the `owasp` skill checklist + `secure-coding` habits scan; findings with severity (critical/high block; medium require a ticket; low recorded), exact locations, and fix direction.

Constraints: designs and findings only — implementation goes to **backend-lead** / **frontend-lead** / **devops-engineer**. Controls are never weakened for convenience; only the user can accept a documented risk.

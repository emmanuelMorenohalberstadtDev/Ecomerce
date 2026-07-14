---
description: Create or update documentation — README, API reference, changelog, Gate 7 (documentation-engineer agent)
argument-hint: "[what to document, or 'gate' to run doc-drift check on recent changes]"
---

Documentation task: $ARGUMENTS

Invoke the **documentation-engineer** agent. Depending on the request it delivers:

- **Feature/API docs**: human-readable reference from the OpenAPI spec with copy-pasteable request/response examples (including one error case per endpoint); behavior + why, never code line-by-line.
- **README/setup**: zero-context onboarding — a new developer must reach a running stack (`docker compose up`) from it alone; verify steps against the actual compose setup with **devops-engineer** if uncertain.
- **Changelog**: Keep-a-Changelog entries curated from Conventional Commits (human-readable, SemVer-aligned) — not raw commit dumps.
- **Gate 7 check**: audit recent merges for doc drift — API changes without reference updates, behavior changes without README/feature-doc updates, decisions without ADRs; report findings per change.

Constraints: technical ambiguity goes back to the authoring agent — this agent never invents behavior or documents unimplemented features as existing. All artifacts in English (global rules).

# Refactor: <short title>

> Branch: `refactor/<slug>` · Rule: **behavior must not change**

## Motivation

What quality problem exists (duplication, layer violation, god class, unclear naming, test pain). Link to the code smell locations.

## Scope

- Files/modules in scope:
- Explicitly out of scope:
- Public API affected: no / yes → then this is NOT a pure refactor; use `templates/feature.md` + SemVer analysis

## Safety Net

Refactoring without tests is rewriting. Before touching code:

- [ ] Existing tests cover the current behavior of the affected code
- [ ] If not: characterization tests written first (list them)

## Plan

Small, independently-committable steps (each commit compiles and passes tests):

| # | Step | Commit message |
|---|------|---------------|
| 1 | | `refactor(scope): …` |

## Verification

- [ ] Full test suite green before and after — same tests, no assertions weakened
- [ ] No behavior change observable via API/UI
- [ ] Coverage did not decrease

## Quality Gates

- [ ] Architect consulted if module boundaries move
- [ ] Gate 2 self-check passed
- [ ] Gate 6 code review approved (reviewer verifies zero behavior change)

See `docs/quality-gates.md`.

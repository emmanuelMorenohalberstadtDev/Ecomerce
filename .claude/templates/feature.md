# Feature: <name>

> Owner: <agent/person> · Story: <link to user story> · Branch: `feature/<ticket>-<slug>`

## Summary

One paragraph: what the feature does and for whom.

## User Story Reference

- Story ID / link:
- Acceptance criteria (copied, each one testable):
  - [ ] AC-1 …
  - [ ] AC-2 …

## Design

- **Architecture impact**: none / ADR link
- **Affected bounded contexts**:
- **API changes**: endpoints added/changed (link to `templates/api.md` doc if new)
- **Schema changes**: none / migration `V___.sql` (link to `templates/database-migration.md` doc)
- **UI/UX**: design spec link, states covered (loading / empty / error / success)
- **New dependencies**: none / justification + architect approval

## Implementation Plan

| # | Task | Owner | Depends on |
|---|------|-------|-----------|
| 1 | | | |

## Test Plan Reference

Link to test plan (`templates/test-plan.md`). Coverage target: ≥ 80% on domain/application.

## Rollout & Risk

- Feature flag: yes/no
- Rollback strategy:
- Known risks:

## Quality Gates

- [ ] Gate 0 — story + decomposition approved
- [ ] Gate 1 — design artifacts complete
- [ ] Gate 2 — implementation self-check passed
- [ ] Gate 3 — tests green in CI, coverage met
- [ ] Gate 4 — security review passed
- [ ] Gate 5 — performance budget respected (if applicable)
- [ ] Gate 6 — code review approved
- [ ] Gate 7 — documentation updated

See `docs/quality-gates.md`.

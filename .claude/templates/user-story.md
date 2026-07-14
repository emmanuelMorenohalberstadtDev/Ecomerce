# User Story ST-NNN: <title>

> Epic: <link> · Priority: Must / Should / Could / Won't · Owner: product-owner

## Story

**As a** <role: guest / customer / admin>
**I want** <capability>
**so that** <benefit>.

## Acceptance Criteria

Gherkin-style, each independently testable. Cover the sad paths too.

```gherkin
AC-1:
  Given <precondition>
  When <action>
  Then <observable outcome>

AC-2 (error case):
  Given …
  When …
  Then …
```

## UX Notes

States to design: loading / empty / error / success. Accessibility requirements. Link to design spec once ui-ux-designer delivers.

## Technical Notes

Filled by orchestrator/architect during decomposition — API endpoints, schema impact, affected contexts. The story itself stays implementation-agnostic.

## Definition of Ready

- [ ] Acceptance criteria testable and reviewed by qa-engineer
- [ ] Sized/decomposable in ≤ 1 iteration
- [ ] No unresolved dependency blocks it

## Definition of Done

- [ ] All acceptance criteria have passing automated tests
- [ ] All quality gates passed (`docs/quality-gates.md`)
- [ ] Documentation updated
- [ ] Demoable from a clean environment (docker compose up)

---
description: Safe refactor — impact analysis, execution, verification (architect → owning lead → reviewer)
argument-hint: "[what to refactor and why]"
---

Refactor safely: $ARGUMENTS

Pipeline (`.claude/templates/refactor.md` governs — behavior must not change):

1. **Impact — invoke the software-architect agent**: assess scope, module boundaries moved, public API impact (if the API changes, this is NOT a pure refactor — stop and reroute as a feature with SemVer analysis). Output: scope, out-of-scope, risks.
2. **Safety net check**: verify existing tests cover current behavior of the affected code. Gaps → **test-engineer** writes characterization tests FIRST.
3. **Execute — invoke the owning lead** (backend-lead or frontend-lead per the code): small, independently-committable steps (`refactor(scope): …` commits), each compiling and passing the full suite. No assertions weakened, no behavior change, no opportunistic feature work.
4. **Verify — invoke the reviewer agent**: confirm zero behavior change, coverage did not decrease, conventions held.

Constraints: refactoring without a safety net is rewriting — step 2 is not skippable. Discovery of actual bugs mid-refactor → separate `templates/bug-fix.md` flow, not mixed into refactor commits.

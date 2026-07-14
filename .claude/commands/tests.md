---
description: Plan and implement tests (qa-engineer plans → test-engineer implements)
argument-hint: "[the feature/change to test, or 'plan only' / 'implement plan X']"
---

Test the following: $ARGUMENTS

Two-phase, strictly in order:

1. **Plan — invoke the qa-engineer agent**: produce/refresh the test plan using `.claude/templates/test-plan.md` and skill `testing-strategy`. Every acceptance criterion maps to ≥ 1 case; mandatory categories addressed (validation, authN/authZ per the auth decision table, boundaries, concurrency where state is shared, idempotency on money paths) or explicitly N/A with a reason. If acceptance criteria are untestable, bounce to **product-owner** and stop.
2. **Implement — invoke the test-engineer agent** with the plan: write the tests at the tiers the plan assigns (skills `junit`, `mockito`, `testcontainers`), plan case IDs traceable in test names, deterministic (fixed clocks, no sleeps), JaCoCo floor respected (skill `jacoco`). Run the suites and report results honestly — failures included, with output.

Constraints: test-engineer never modifies production code — testability friction becomes a report to the owning lead with a suggested seam. Unplanned risky paths discovered while implementing go back to qa-engineer as plan proposals.

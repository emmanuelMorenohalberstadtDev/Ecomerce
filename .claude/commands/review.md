---
description: Final code review of a diff/PR (reviewer agent; escalates security findings)
argument-hint: "[PR number, branch, or description of the change to review]"
---

Run the final code review gate (Gate 6) on: $ARGUMENTS (if empty, review the current uncommitted/branch diff).

1. Invoke the **reviewer** agent with the diff context. It must review against `.claude/docs/conventions.md`, `.claude/docs/global-rules.md`, and applicable ADRs, loading the stack skills matching the diff (`solid`, `clean-architecture`, plus `spring-boot`/`angular`/`postgresql`/`docker` as relevant).
2. If the diff touches auth, input handling, money, or data exposure, ALSO invoke the **security-engineer** agent to run its Gate 4 checklist (skill `owasp`) — its findings are reported separately with severity.
3. Consolidate into one report: verdict (approve / request changes), findings classified blocker/should/nit with `file:line` references and the violated rule cited.

Constraints: neither agent fixes code — findings only. Structural disagreements are flagged for **software-architect**, not adjudicated here.

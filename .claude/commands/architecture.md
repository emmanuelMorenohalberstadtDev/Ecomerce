---
description: Architectural analysis, design, or ADR for a structural decision (software-architect agent)
argument-hint: "[the structural question, feature to design, or dependency to evaluate]"
---

Invoke the **software-architect** agent for: $ARGUMENTS

The agent must:
1. Load skills `clean-architecture`, `hexagonal`, `ddd`, `solid`, `design-patterns` as relevant, plus existing ADRs in `docs/adr/`.
2. Deliver the appropriate artifact:
   - Structural decision → ADR using `.claude/templates/adr.md` (options honestly compared, including "do nothing").
   - Feature design → module/interface definition: bounded contexts touched, ports, layer placement.
   - Dependency request → approve/reject with written justification against the global rules.
   - Architecture review → violations listed with locations and the rule breached.
3. Consult **security-engineer** before finalizing anything touching auth, secrets, or data exposure.

Constraints: no production code — interfaces, package layouts, and decisions only. The fixed stack (`.claude/docs/global-rules.md`) is not negotiable without user approval.

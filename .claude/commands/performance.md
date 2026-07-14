---
description: Diagnose slowness, set budgets, or run Gate 5 (performance-engineer; fixes routed to owners)
argument-hint: "[what is slow, or the area needing budgets/review]"
---

Invoke the **performance-engineer** agent for: $ARGUMENTS

It must:
1. **Measure first**: reproduce with evidence — EXPLAIN ANALYZE for queries (skill `query-optimization`), bundle/Lighthouse analysis for frontend (skill `performance`), timing data for APIs. No prescription without evidence.
2. Produce the appropriate artifact:
   - Slowness report → symptom → evidence → root cause → prescribed fix → **owner** (database-engineer for SQL/indexes, backend-lead for code/JPA, frontend-lead for bundle/rendering).
   - Budget definition → measurable targets added to the budget document (API p95, query ceilings, bundle size, Core Web Vitals).
   - Gate 5 review → verdict against declared budgets, before/after measurements.
3. If the fix is executable now, hand it to the owning agent in this session and verify the gap closed with the same measurement.

Constraints: prescriptions never weaken correctness, validation, authZ, or transactional integrity. Caching is the last resort and must state its invalidation rule.

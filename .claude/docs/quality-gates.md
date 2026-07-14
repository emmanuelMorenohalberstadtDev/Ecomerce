# Quality Gates

Every change passes through these gates **in order**. A gate failure returns the work to its owner; gates are never skipped or weakened (see `global-rules.md`).

## Gate 0 — Definition (before any code)

Owner: product-owner + orchestrator

- [ ] User story exists with testable acceptance criteria (`templates/user-story.md`)
- [ ] Task decomposed and assigned; dependencies identified
- [ ] Architectural impact assessed; ADR created if a structural decision is involved

## Gate 1 — Design

Owner: software-architect / ui-ux-designer / database-engineer (as applicable)

- [ ] Module boundaries and interfaces defined; no layer violations in the design
- [ ] API contract drafted (OpenAPI) before frontend work starts
- [ ] Schema changes expressed as a Flyway migration draft
- [ ] UI work has a design spec (states: loading, empty, error, success) and a11y notes
- [ ] New dependencies justified in writing and approved

## Gate 2 — Implementation (self-check by the implementer)

- [ ] Follows `docs/conventions.md` (naming, structure, commit format)
- [ ] SOLID respected; domain layer framework-free
- [ ] No dead code, no TODO without a ticket, no commented-out blocks
- [ ] Input validation at every boundary; errors as RFC 9457 Problem Details
- [ ] No secrets or environment-specific values in code

## Gate 3 — Testing

Owner: qa-engineer (plan) + test-engineer (execution)

- [ ] Test plan followed (`templates/test-plan.md`)
- [ ] Unit tests: domain + application layers ≥ 80% line coverage (JaCoCo)
- [ ] Integration tests with Testcontainers for every new repository/adapter
- [ ] API tests for every new/changed endpoint (happy path + error cases)
- [ ] All tests pass in CI, not just locally

## Gate 4 — Security

Owner: security-engineer

- [ ] OWASP Top 10 checklist run against the diff
- [ ] AuthZ verified: every endpoint declares its access rule; ownership checks on user-scoped resources
- [ ] No sensitive data in logs, URLs, or error responses
- [ ] Dependencies scanned; no known critical CVEs introduced

## Gate 5 — Performance (when the change touches a declared budget)

Owner: performance-engineer

- [ ] Queries reviewed (no N+1, EXPLAIN on new queries touching large tables)
- [ ] Frontend budget respected (bundle size, LCP/INP where applicable)
- [ ] Pagination on every unbounded collection endpoint

## Gate 6 — Review

Owner: reviewer

- [ ] PR template complete (`templates/pull-request.md`)
- [ ] Diff is one logical change; commits follow Conventional Commits
- [ ] Readability: a new team member could understand the change without the author
- [ ] All previous gate evidence present in the PR

## Gate 7 — Documentation

Owner: documentation-engineer

- [ ] OpenAPI spec updated in the same PR as the API change
- [ ] README / feature docs updated if behavior or setup changed
- [ ] ADR merged if a decision was made
- [ ] Changelog entry added (SemVer-appropriate)

## CI enforcement

devops-engineer wires gates 2–5 into GitHub Actions: build, tests, coverage threshold, dependency audit, lint. A red pipeline blocks merge — no manual overrides.

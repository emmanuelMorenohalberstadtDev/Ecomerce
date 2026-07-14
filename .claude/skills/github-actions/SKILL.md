---
name: github-actions
description: CI/CD pipelines with GitHub Actions — gate enforcement order, caching, integration-test wiring, required checks, and workflow hygiene.
---

# GitHub Actions

## Purpose

The pipeline mechanically enforces the quality gates: if it's green it passed the rules, if it's red it doesn't merge — no human memory involved.

## When to Use

Creating/changing workflows, wiring a new gate check, speeding up CI, debugging pipeline failures.

## Rules

1. **Cheapest checks first, fail fast**: lint/format → unit tests → integration tests (Testcontainers) → coverage check (JaCoCo floor) → dependency audit → image builds. A typo shouldn't cost a 10-minute integration run.
2. **Triggers**: `pull_request` runs the full verification; `push` to `main` additionally builds/publishes images. Nothing runs only on main that could have failed the PR.
3. **Required checks = the gates**: branch protection marks build, tests, coverage, and audit as required. Editing the workflow to skip a gate is a global-rules violation (devops constraint).
4. **Caching keyed on lockfiles**: `actions/setup-java` + Maven cache keyed on `pom.xml` hash; `actions/setup-node` + npm cache on `package-lock.json`. Never cache build outputs that encode test results.
5. **Pinned action versions** (`actions/checkout@v4`, or SHA-pinned per security-engineer's supply-chain bar) — never `@main`.
6. **Least-privilege `permissions`** block per workflow (`contents: read` default; grant only what a job uses). Secrets via GitHub Secrets, referenced — never echoed, never in artifact logs.
7. **Testcontainers works out of the box** on ubuntu runners (Docker daemon present) — integration tests run real Postgres in CI, same tag as compose.
8. DRY workflows: repeated job patterns become reusable workflows/composite actions once repeated (extract on the second copy, per global DRY rule).

## Examples

```yaml
name: verify
on: { pull_request: {}, push: { branches: [main] } }
permissions: { contents: read }

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - name: Unit tests
        run: mvn -B verify -Dgroups=unit
      - name: Integration tests (Testcontainers)
        run: mvn -B verify -Dgroups=integration
      - name: Coverage gate
        run: mvn -B jacoco:check
      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with: { name: jacoco, path: target/site/jacoco }

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: npm }
      - run: npm ci
      - run: npm run lint && npm test -- --watch=false && npm run build
```

## Best Practices

- `concurrency` groups cancel superseded runs per branch (`group: ${{ github.ref }}`, `cancel-in-progress: true`) — don't verify commits nobody will merge.
- Surface failures readably: JaCoCo/lint summaries into `$GITHUB_STEP_SUMMARY`, not buried in 5,000 log lines.
- Keep the cached full run under ~10 minutes (devops success criterion); parallelize backend/frontend jobs, split test tiers when they grow.
- Build the *same* Dockerfiles compose uses — CI images and local images may not drift.

## Common Mistakes

- Skipping integration tests in CI "for speed" — the gate exists precisely there.
- `pull_request_target` with checkout of untrusted code (secret exfiltration classic).
- Fixing a red build with `continue-on-error`/`|| true` instead of the root cause.
- Cache keys without lockfile hashes (stale dependency caches masking resolution breakage).
- Matrix builds for a single fixed stack (this project has exactly one Java/Node target — YAGNI).

## References

- docs.github.com/actions; Testcontainers CI docs
- See skills `docker`, `testcontainers`, `jacoco`, `docker-compose`

# Bug Fix: <short title>

> Severity: critical / high / medium / low · Branch: `fix/<ticket>-<slug>`

## Symptom

What the user/system observes. Include exact error messages, status codes, screenshots.

## Reproduction

1. Preconditions (data, auth state, environment)
2. Steps
3. Expected vs actual

## Root Cause

The *actual* cause, not the symptom. Reference the exact file/line/query.
If the root cause is unknown, the fix is not ready — keep investigating.

## Fix

- What changed and why this addresses the root cause (not a workaround).
- Why this is the minimal correct change.

## Regression Test

Mandatory: a test that **fails without the fix and passes with it**.

- Test file/name:
- Level: unit / integration / API

## Impact Analysis

- Other call sites of the changed code:
- Could the same root cause exist elsewhere? Checked where:

## Quality Gates

- [ ] Root cause identified and documented (no symptom-patching)
- [ ] Regression test added and verified to fail pre-fix
- [ ] Gate 2 implementation self-check passed
- [ ] Gate 4 security check (if the bug touches auth/input/data exposure)
- [ ] Gate 6 code review approved
- [ ] Changelog entry (PATCH bump)

See `docs/quality-gates.md`.

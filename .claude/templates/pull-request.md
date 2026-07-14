# Pull Request

## Title

`<type>(<scope>): <subject>` — must follow Conventional Commits.

## What & Why

2–5 sentences: what changes and the reason. Link the story/bug/refactor document.

## Type of Change

- [ ] Feature  - [ ] Bug fix  - [ ] Refactor  - [ ] Docs  - [ ] Infra/CI  - [ ] Performance

## Changes

Bullet list of the meaningful changes (not a file list — reviewers can see the diff).

## How Was This Tested

- Unit / integration / API tests added or updated (names):
- Manual verification steps (if any):
- Coverage on touched domain/application code: ___% (target ≥ 80%)

## Breaking Changes

None / description + migration path + SemVer impact (MAJOR).

## Screenshots / Recordings

Mandatory for any UI change: before & after, including mobile viewport.

## Checklist (author)

- [ ] One logical change; commits follow Conventional Commits
- [ ] Self-reviewed the diff line by line
- [ ] No secrets, no dead code, no unrelated formatting noise
- [ ] Conventions followed (`docs/conventions.md`)
- [ ] Tests green in CI
- [ ] OpenAPI / docs updated if applicable
- [ ] Gate evidence attached (security, performance when applicable)

## Reviewer Notes

Areas needing extra attention; known trade-offs; anything intentionally deferred (with ticket).

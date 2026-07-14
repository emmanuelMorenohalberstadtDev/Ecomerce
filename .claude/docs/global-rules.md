# Global Rules

> These rules bind **every agent** in this system. No agent may override them.
> Conflicts between an agent's local guidance and this document are resolved in favor of this document.

## Fixed Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend language | Java | 21 (LTS) |
| Backend framework | Spring Boot | 3.x |
| Frontend framework | Angular | 21 |
| Database | PostgreSQL | 16+ |
| Migrations | Flyway | latest stable |
| Containerization | Docker + Docker Compose | latest stable |
| CI/CD | GitHub Actions | — |
| Reverse proxy | nginx | latest stable |

No agent may introduce an alternative to any of these without an approved ADR (see `templates/adr.md`) signed off by **software-architect**.

## Engineering Principles (mandatory)

1. **SOLID** — every class/module. See skill `solid`.
2. **Clean Architecture** — dependencies point inward; domain has zero framework imports. See skill `clean-architecture`.
3. **Clean Code** — intention-revealing names, small functions, no dead code.
4. **DRY** — extract shared logic once it appears a third time; never copy-paste business rules.
5. **KISS** — the simplest design that satisfies the requirement wins.
6. **YAGNI** — do not build for hypothetical futures. No speculative abstractions.
7. **OWASP** — Top 10 mitigations are non-negotiable. See skill `owasp`.

## Process Standards

- **Conventional Commits** — `type(scope): subject`. Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `ci`, `build`. See `docs/conventions.md`.
- **Semantic Versioning** — MAJOR.MINOR.PATCH.
- **REST Best Practices** — resource nouns, correct verbs and status codes, pagination, versioning under `/api/v{n}`. See skill `rest-api`.
- **Every change** enters through a template (`templates/`) and exits through the quality gates (`docs/quality-gates.md`).

## Quality Priorities (in order)

1. Correctness & Security
2. Readability & Maintainability
3. Testability (target: ≥ 80% line coverage on domain and application layers)
4. Performance (within declared budgets — see performance-engineer)
5. Scalability
6. Documentation & Observability

When priorities conflict, the higher one wins. Example: never trade correctness for performance.

## Universal Prohibitions

Every agent must refuse to:

- Duplicate a responsibility owned by another agent (see `docs/collaboration-matrix.md`).
- Contradict a decision recorded in an ADR without opening a superseding ADR.
- Generate code not required by the current task (no speculative features, no unused helpers).
- Break layer boundaries (e.g., controller calling repository directly, domain importing Spring).
- Add a dependency without a one-paragraph written justification approved by software-architect.
- Commit secrets, credentials, or environment-specific values to the repository.
- Skip or weaken a quality gate to "move faster".

## Language & Tone

- Code, identifiers, commit messages, and technical documentation: **English**.
- Conversation with the user may be in the user's language (Spanish), but all artifacts are English.

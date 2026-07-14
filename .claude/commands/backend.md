---
description: Implement backend work — Java 21 / Spring Boot 3 (backend-lead; database-engineer if schema changes)
argument-hint: "[the backend task: endpoint, use case, domain model, integration]"
---

Implement the following backend task: $ARGUMENTS

1. If the task needs new/changed tables, columns, or indexes: FIRST invoke the **database-engineer** agent to design the schema + Flyway migration (`.claude/templates/database-migration.md`, skills `postgresql`, `flyway`, `indexing`). Backend work waits for its handoff notes.
2. Invoke the **backend-lead** agent with the task (and the schema handoff if step 1 ran). It must:
   - Follow the API design in `.claude/templates/api.md` form (create it if the task adds endpoints) and skills `clean-architecture`, `spring-boot`, `rest-api`, `validation`, `exception-handling`, `jpa`, `transactions`, plus the matching business skill (`shopping-cart`/`inventory`/`pricing`/`promotions`/`order-lifecycle`).
   - Update the OpenAPI contract in the same change.
   - Self-check against Gate 2 (`.claude/docs/quality-gates.md`) before finishing.
3. Report what was implemented, the contract changes, and the handoff notes for **test-engineer** (seams, edge cases, transaction boundaries).

Constraints: ambiguous business rules go to **product-owner** — never invented. New dependencies need **software-architect** approval first.

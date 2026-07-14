# Database Migration: <short title>

> Owner: database-engineer · File: `V<version>__<snake_case_description>.sql`

## Purpose

What schema change and which feature/story requires it.

## Change Summary

| Object | Action | Details |
|--------|--------|---------|
| table `carts` | add column | `expires_at timestamptz` |

## Migration SQL

```sql
-- paste the exact migration content
```

## Safety Analysis

- **Destructive?** (drops/renames/type-narrowing): no / yes → describe the expand–migrate–contract plan
- **Locking**: does any statement take a long lock on a hot table? (e.g., `ALTER TABLE` rewrites, `CREATE INDEX` without `CONCURRENTLY`)
- **Data backfill**: needed? batched? idempotent?
- **Rollback**: forward-fix strategy (Flyway does not auto-undo)

## Application Impact

- JPA entities to update (handoff to backend-lead):
- Queries affected:
- Deploy ordering constraint: migration before/with app version X

## Quality Gates

- [ ] Naming follows `docs/conventions.md` (tables, columns, indexes, migration file)
- [ ] Never edits an already-applied migration
- [ ] Tested against a Testcontainers PostgreSQL from a clean state AND from previous version
- [ ] Indexes justified (skill `indexing`) — no speculative indexes
- [ ] Reviewer + backend-lead informed of entity mapping changes

See `docs/quality-gates.md`.

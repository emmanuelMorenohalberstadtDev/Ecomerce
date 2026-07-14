---
name: flyway
description: Schema evolution with Flyway — versioning discipline, safe migration patterns, expand–migrate–contract for destructive changes, and testing migrations.
---

# Flyway

## Purpose

Every schema change is a versioned, immutable, forward-only SQL file — the database's git history. No manual DDL ever touches any environment.

## When to Use

Any schema or reference-data change; planning destructive changes; configuring Flyway; debugging checksum/validation errors.

## Rules

1. **Naming**: `V<version>__<snake_case_description>.sql` in `db/migration` (`V007__add_cart_expiration.sql`); zero-padded sequential versions; `R__<description>.sql` for repeatable, idempotent reference data.
2. **Immutability**: an applied migration is never edited (checksum validation will — correctly — refuse to start). Fixes are new versions. `flyway repair` is a local-dev tool, not a workflow.
3. **App config**: `spring.jpa.hibernate.ddl-auto: validate` — Flyway owns DDL; Hibernate only verifies mappings match.
4. **One concern per migration**: a table creation, an index, a backfill — separate files. Small migrations are diagnosable migrations.
5. **Compatibility rule**: a migration must not break the *currently running* app version (deploys aren't atomic). Destructive changes use **expand–migrate–contract**:
   - *Expand* (release N): add new column/table, dual-write if needed.
   - *Migrate* (N): backfill in batches; verify.
   - *Contract* (release N+1, after code stops using the old shape): drop old column.
6. **Safety analysis mandatory** per `templates/database-migration.md`: locks taken, backfill volume, rollback story (forward-fix — Flyway doesn't undo).
7. Long/locking operations flagged: `create index concurrently` cannot run inside a transaction → `-- flyway:executeInTransaction=false` header on that migration.

## Examples

```sql
-- V008__add_orders_status_check.sql  (expand: additive, safe)
alter table orders
    add constraint ck_orders_status
    check (status in ('PLACED','PAID','SHIPPED','DELIVERED','CANCELLED'))
    not valid;                       -- doesn't lock-scan existing rows
alter table orders validate constraint ck_orders_status;  -- separate, weaker lock
```

```sql
-- V009__backfill_carts_expires_at.sql  (batched backfill, idempotent per run)
update carts set expires_at = updated_at + interval '7 days'
where expires_at is null and status = 'ACTIVE';
```

## Best Practices

- Test every migration two ways with Testcontainers: clean database from V001, and previous-version → new (test-engineer wires this).
- Keep seed data for dev in `R__` repeatables keyed on natural ids (`insert ... on conflict do nothing`).
- Baseline (`flyway baseline`) only for adopting an existing database — never mid-project to "clean up".
- Reference the migration version in the PR description; reviewer checks naming + safety analysis.

## Common Mistakes

- Editing V005 because "it hasn't shipped to prod yet" — it shipped to every teammate's machine; checksum chaos.
- `drop column` in the same release where code stops reading it (old pods still running during deploy).
- Million-row backfills in one statement/transaction inside a startup migration (startup timeout + lock storm).
- Environment-conditional SQL inside migrations — migrations are identical everywhere; data differences go in repeatables/config.
- Letting Hibernate `ddl-auto: update` "help" in dev, then discovering drift when Flyway runs in CI.

## References

- flywaydb.org docs; PostgreSQL ALTER TABLE lock documentation
- See skills `postgresql`, `indexing`, `testcontainers`

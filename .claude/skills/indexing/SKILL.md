---
name: indexing
description: PostgreSQL indexing strategy — B-tree fundamentals, composite column order, partial/covering indexes, and when NOT to index.
---

# Indexing

## Purpose

Make the queries the application actually runs fast, at the minimum write/storage cost — every index is a bet on a query pattern, and bets are written down.

## When to Use

Designing schemas for known query patterns, executing performance-engineer's prescriptions, auditing existing indexes.

## Rules

1. **Every index names its query**: no index ships without the query (or FK/constraint reason) it serves, recorded in the migration doc. Speculative indexes are rejected.
2. **Composite column order = equality first, then range, then sort**: for `WHERE customer_id = ? AND created_at > ? ORDER BY created_at DESC` → `(customer_id, created_at desc)`. A leading column not in the WHERE makes the index useless for that query.
3. **Partial indexes for hot subsets**: most queries touch active rows — `where status = 'ACTIVE'` partial indexes are smaller, faster, and encode business reality (see one-active-cart example in skill `postgresql`).
4. **Covering (`include`)** when a hot query needs 1–2 extra columns: index-only scans skip the heap — `create index ... on orders (customer_id, created_at desc) include (status, total)`.
5. **Expression indexes match the query's expression exactly**: `lower(email)` queries need an index on `lower(email)`.
6. **Default B-tree**; GIN only for JSONB containment / full-text search (product search may justify one — via ADR); no exotic types without measured need.
7. **On hot tables, `create index concurrently`** (outside a transaction — see skill `flyway`); plain `create index` blocks writes for the duration.
8. **Removal is part of the job**: unused indexes (pg_stat_user_indexes idx_scan ≈ 0 over a real period) cost every write — drop them via migration with the evidence noted.

## Examples

```sql
-- order history: equality + sort, covering the list view
create index idx_orders_customer_created
    on orders (customer_id, created_at desc) include (status, total, currency);

-- product search by category, in-stock first (partial + composite)
create index idx_products_category_active
    on products (category_id, price) where active = true;

-- case-insensitive login lookup
create unique index uq_users_email_lower on users (lower(email));
```

Verification (always, before/after):

```sql
explain (analyze, buffers) select ... ;  -- look for Index Scan / Index Only Scan replacing Seq Scan
```

## Best Practices

- Index FK child columns that get joined/filtered or whose parent takes deletes (Postgres doesn't auto-index FKs).
- Match `desc` in the index to the dominant sort direction to avoid backward scans on hot lists.
- Test on realistic volume: the planner ignores indexes on 200-row dev tables — seed data properly (with performance-engineer).
- Uniqueness that *is* a business rule belongs to a unique index/constraint, not just app validation.

## Common Mistakes

- Single-column indexes on every WHERE column, expecting Postgres to combine them well (bitmap ANDs lose to one good composite).
- Redundant prefixes: `(a)` alongside `(a, b)` — the composite covers the single.
- Indexing low-selectivity flags alone (`active`) instead of using them as partial-index predicates.
- Wrapping the column in a function in the query (`date(created_at) = ?`) and wondering why the plain index is unused — range predicates or expression indexes.
- Believing an index "didn't work" from a cold single run — compare plans, not one-off timings.

## References

- use-the-index-luke.com; postgresql.org index docs
- See skills `query-optimization`, `postgresql`, `flyway`

---
name: query-optimization
description: Diagnosing and fixing slow PostgreSQL queries — reading EXPLAIN ANALYZE, common plan pathologies, rewrite patterns, and app-level query hygiene.
---

# Query Optimization

## Purpose

Turn "it's slow" into a read plan, a named pathology, and a verified fix — evidence in, evidence out.

## When to Use

A query misses its budget; reviewing JPA-generated SQL; designing queries over large tables (orders, order_items, products).

## Rules

1. **EXPLAIN (ANALYZE, BUFFERS) or it didn't happen**: no optimization without the actual plan on realistic data volume. Estimated-vs-actual row mismatches (off by 100×) point to stale stats (`analyze`) or unmodeled correlations.
2. **Read plans inside-out**; the usual suspects:
   - `Seq Scan` on a big table with a selective filter → missing/unusable index (see skill `indexing`).
   - `Rows Removed by Filter` huge → the index isn't covering the real predicate.
   - `Nested Loop` with a big outer side → join misestimate; check stats and indexes on the inner side.
   - `Sort` spilling (`external merge Disk`) → index providing the order, or smaller working set.
3. **Fix in this order**: (1) the query shape, (2) the index, (3) the schema (denormalize/materialize), (4) caching — each step only when the previous can't meet the budget. Caching first is how you cache a bug.
4. **App-level hygiene counts as query optimization**: N+1s (fetch joins/`@EntityGraph`, see skill `jpa`), selecting entities where projections suffice, missing pagination, chatty per-row round trips → batch.
5. **Pagination on big tables**: `LIMIT/OFFSET` degrades linearly with depth — keyset pagination (`where (created_at, id) < (?, ?) order by created_at desc, id desc limit 20`) for deep/infinite scroll paths.
6. **Only needed columns** in hot queries: `select *` defeats index-only scans and inflates transfer.
7. Every fix ships with before/after `EXPLAIN ANALYZE` in the tuning report (contract with performance-engineer).

## Examples

```sql
-- pathology: OFFSET 10000 reads and discards 10000 rows
select id, total, created_at from orders
where customer_id = $1 order by created_at desc limit 20 offset 10000;

-- fix: keyset
select id, total, created_at from orders
where customer_id = $1 and (created_at, id) < ($2, $3)
order by created_at desc, id desc limit 20;
```

```sql
-- pathology: function on the column kills the index
where date(created_at) = current_date
-- fix: range predicate, index-friendly
where created_at >= current_date and created_at < current_date + interval '1 day'
```

## Best Practices

- Keep a top-offenders list from `pg_stat_statements` (performance-engineer owns the list; this skill fixes entries).
- Test the fix at the p95 data shape (the customer with 3,000 orders, the category with 40k products), not the average.
- `EXISTS` over `IN (subquery)` for large sets; `count(*)` estimates (`reltuples`) where exact counts aren't required by the UX.
- When JPA can't express the efficient query cleanly, a native query in the adapter (database-engineer reviews) beats contorting JPQL.

## Common Mistakes

- Adding indexes to fix a query that filters in the app instead of the WHERE clause.
- Optimizing on dev-sized data, shipping, and being surprised.
- `distinct` slapped on to hide a join fanout bug — fix the join.
- Rewriting a clear query into an unreadable one for a 3% gain (readability is a global priority).
- Trusting the first run (cold cache) or the tenth (all cache) — report both or use BUFFERS to reason.

## References

- postgresql.org EXPLAIN docs; use-the-index-luke.com; pg_stat_statements
- See skills `indexing`, `jpa`, `postgresql`

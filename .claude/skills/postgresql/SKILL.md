---
name: postgresql
description: PostgreSQL schema design for the ecommerce — type choices, constraints as integrity guarantees, naming, and Postgres-specific features worth using.
---

# PostgreSQL

## Purpose

Design schemas where invalid data is *impossible to insert* — the database is the last line of defense and the longest-lived artifact in the system.

## When to Use

Designing tables, choosing types, adding constraints, using Postgres features (enums vs checks, JSONB, generated columns).

## Rules

1. **Types are contracts**:
   - Money: `numeric(19,4)` + `char(3)` currency — never `float`/`double precision`/`money`.
   - Time: `timestamptz` always (UTC in, UTC out); `date` for date-only facts.
   - Text: `text` with a `check (char_length(...) <= n)` or `varchar(n)` per conventions — no unbounded user text without a limit.
   - IDs: per project ADR (`bigint generated always as identity` or UUIDv7).
2. **Constraints over conventions**: `not null` by default (nullable is the exception, justified); FKs always declared (with explicit `on delete` behavior — no accidental cascades); `unique` where business says unique (email, sku, active-cart-per-user); `check` for domains (`quantity > 0`, `price >= 0`).
3. **Status columns**: `text` + check constraint listing values (easier to evolve than native enums) — transitions enforced in the domain, valid values enforced here.
4. **Naming per `docs/conventions.md`**: plural snake_case tables, `idx_/uq_/ck_/fk_` prefixes, `created_at`/`updated_at` on every table.
5. **JSONB only for genuinely schemaless data** (e.g., product attributes varying by category), with a documented shape — never for data you filter/join relationally.
6. FK columns get an index (Postgres doesn't create one automatically) when the FK is queried from the child side or the parent gets deletes/updates.

## Examples

```sql
create table order_items (
    id            bigint generated always as identity primary key,
    order_id      bigint not null references orders (id) on delete cascade,
    product_id    bigint not null references products (id) on delete restrict,
    product_name  text   not null,                          -- snapshot at purchase time
    unit_price    numeric(19,4) not null check (unit_price >= 0),
    currency      char(3) not null,
    quantity      int    not null check (quantity > 0),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint uq_order_items_order_product unique (order_id, product_id)
);
create index idx_order_items_product_id on order_items (product_id);
```

```sql
-- one active cart per customer: partial unique index as a business rule
create unique index uq_carts_customer_active on carts (customer_id) where status = 'ACTIVE';
```

## Best Practices

- Snapshot purchase-time facts (name, price) into order rows — orders must survive catalog edits (see skill `ddd`).
- Generated columns for derived values you index (`total numeric generated always as (unit_price * quantity) stored`).
- `citext` (or lower() unique index) for case-insensitive email uniqueness.
- Comment non-obvious columns/constraints in DDL (`comment on column ...`) — the schema is documentation.

## Common Mistakes

- Nullable FKs standing in for polymorphism — model the relationship properly.
- `on delete cascade` from reference data (deleting a product wipes order history).
- Soft-delete flags without partial indexes and without excluding them from unique constraints.
- Storing serialized lists in text columns ("1,4,7") — junction tables exist.
- Timestamps without time zone scattered across servers in different zones.

## References

- postgresql.org docs (DDL, constraints); wiki.postgresql.org "Don't Do This"
- See skills `flyway`, `indexing`, `query-optimization`, `ddd`

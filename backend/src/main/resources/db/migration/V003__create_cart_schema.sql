-- =============================================================================
-- V003__create_cart_schema.sql
-- Context  : cart
-- Purpose  : Bootstrap the cart schema.
--            Creates: carts, cart_items.
--            Grants  : ecommerce_app role with least-privilege surface.
--            Trigger : reuses set_updated_at() defined in V001 for
--                      carts.updated_at.
--
-- Does NOT touch: users, refresh_token_families, refresh_tokens,
-- password_reset_tokens, admin_audit_log, categories, products, or
-- product_images. carts.customer_id references users(id) (V001) and
-- cart_items.product_id references products(id) (V002) as cross-context
-- FKs only (database-design §3.6) -- no existing table is altered here.
--
-- Safety analysis
-- ---------------
-- Lock profile  : Two brand-new, empty tables (carts, cart_items).
--                 ACCESS EXCLUSIVE locks are taken only on these new
--                 relations as they are created; adding the FK from
--                 cart_items to the existing, already-populated users/
--                 products tables (via carts.customer_id and
--                 cart_items.product_id) takes only a brief ROW SHARE lock
--                 on users/products to validate the constraint -- it does
--                 not block reads/writes on those tables and there is no
--                 pre-existing data in carts/cart_items to validate against
--                 (both are created empty in this same migration), so the
--                 FK validation scan is effectively instant. No CONCURRENTLY
--                 needed for any index below -- every index here is built on
--                 a table containing zero rows at migration time.
-- Destructive?  : No. Greenfield creation only; no ALTER/DROP of existing
--                 objects.
-- Backfill?     : No data exists in these tables yet.
-- Forward-fix   : N/A -- no previous version of carts/cart_items to break.
--                 Purely additive from the V001+V002 deployed app's point of
--                 view.
-- Rollback      : DROP TABLE cart_items, then carts (reverse FK dependency
--                 order). Dev only; production follows
--                 expand-migrate-contract -- a new forward version, never an
--                 edit of this file.
-- Testcontainer : Verified from clean (V001 -> V002 -> V003 on an empty
--                 database) and from previous version (schema at V002, then
--                 apply V003) per the flyway skill's mandatory two-path test
--                 rule.
-- Applied       : 2026-07-31  (append-only -- never modify after first apply)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Table: carts
--    Aggregate root of the cart bounded context (database-design §1, §2).
--    Identity is either a registered customer (customer_id) or an anonymous
--    guest (guest_token_hash) -- never both, never neither (ck_carts_identity
--    below). ID is UUIDv7, generated application-side (ADR-0002 + security
--    §6a), same convention as users.id / products.id: no DEFAULT on id --
--    omitting it forces every INSERT to supply one explicitly, so a missing
--    id fails loudly instead of silently falling back to a DB-generated
--    value.
-- ---------------------------------------------------------------------------
CREATE TABLE carts (
    id                uuid          NOT NULL,
    customer_id       uuid,
    -- SHA-256 (or equivalent) hash of the opaque anonymous cart token issued
    -- to a guest. The plaintext token never touches the database -- same
    -- hashing convention as refresh_tokens.token_hash / password_reset_tokens
    -- .token_hash in V001.
    guest_token_hash  text,

    -- ACTIVE / MERGED / ORDERED / ABANDONED (domain-model §2, CartStatus).
    -- text + CHECK is preferred over a native enum -- adding a value is
    -- ALTER TABLE (cheap), not CREATE TYPE + dependency juggling (matches
    -- users.role / products.status convention).
    status            text          NOT NULL DEFAULT 'ACTIVE',

    -- Optimistic-locking column (database-design §4, §7). Maps to @Version
    -- in JPA; concurrent updates to the same cart raise OptimisticLockException
    -- -> mapped to 409 by backend-lead. Not itself a business-rule guard --
    -- purely concurrency control.
    version           bigint        NOT NULL DEFAULT 0,

    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_carts PRIMARY KEY (id),

    -- Reference data (users) is never cascade-deleted (database-design §3.6
    -- rule 6). A customer with cart history cannot be deleted; deactivation
    -- is account_locked = true on users (V001), never a DELETE.
    CONSTRAINT fk_carts_customer
        FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT ck_carts_status
        CHECK (status IN ('ACTIVE', 'MERGED', 'ORDERED', 'ABANDONED')),
    CONSTRAINT ck_carts_version
        CHECK (version >= 0),

    -- Exactly one of customer_id / guest_token_hash is non-null (XOR).
    -- A cart is either a registered customer's cart or a guest's cart, never
    -- both, never neither (database-design §3.1). Same explicit two-branch
    -- AND/OR style as ck_refresh_tokens_used_at in V001, rather than
    -- num_nonnulls(), to keep the failure mode readable in error messages.
    CONSTRAINT ck_carts_identity
        CHECK (
            (customer_id IS NOT NULL AND guest_token_hash IS NULL)
            OR (customer_id IS NULL AND guest_token_hash IS NOT NULL)
        )
);

COMMENT ON TABLE  carts IS
    'Shopping intent for a guest (hashed token) or a registered customer. '
    'Aggregate root of the cart context; owns cart_items. '
    'Never deleted -- lifecycle is a status change (ACTIVE -> MERGED on '
    'login-merge, ACTIVE -> ORDERED on checkout, ACTIVE -> ABANDONED on '
    'expiry sweep), enforced as the DB-level backstop by withholding DELETE '
    'at the grant level (§3) exactly like products/users.';
COMMENT ON COLUMN carts.id IS
    'UUIDv7 generated by the application before INSERT (ADR-0002). '
    'No DB default -- omitting id in an INSERT is a programmer error, not a silent fallback.';
COMMENT ON COLUMN carts.customer_id IS
    'Owning registered customer. NULL for a guest cart. Mutually exclusive '
    'with guest_token_hash (ck_carts_identity). Set on cart-merge at login '
    '(rule 1) when a guest cart is folded into / replaced by the customer''s '
    'cart. ON DELETE RESTRICT: a customer with cart history cannot be deleted.';
COMMENT ON COLUMN carts.guest_token_hash IS
    'Hash of the opaque anonymous cart token issued to a guest browser. '
    'The plaintext token never touches the database. NULL once the cart is '
    'merged into a customer cart. Mutually exclusive with customer_id '
    '(ck_carts_identity).';
COMMENT ON COLUMN carts.status IS
    'ACTIVE (default), MERGED, ORDERED, or ABANDONED. Transitions are a '
    'domain concern (cart use cases); this CHECK only guards the set of '
    'legal values, not the transition graph.';
COMMENT ON COLUMN carts.version IS
    'Optimistic-locking counter (database-design §7). Incremented by JPA on '
    'every UPDATE; a stale write raises OptimisticLockException -> 409. Not '
    'a business-rule column.';

-- Trigger: keep updated_at synchronised on every UPDATE row.
-- Reuses set_updated_at(), defined once in V001; not redefined here.
CREATE TRIGGER trg_carts_updated_at
    BEFORE UPDATE ON carts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Query served: "does this customer already have an active cart?" (point
-- lookup on login/add-to-cart) -- and doubles as the business rule "at most
-- one ACTIVE cart per customer" (database-design §3.1, rule 1). Partial on
-- status = 'ACTIVE' keeps the index limited to the live subset; MERGED/
-- ORDERED/ABANDONED carts (the majority over time) never appear in it. This
-- is the only index needed for customer_id -- a non-partial index is not
-- added, no query pattern calls for scanning a customer's full cart history.
CREATE UNIQUE INDEX uq_carts_customer_active
    ON carts (customer_id)
    WHERE status = 'ACTIVE';

-- Query served: "does this guest token already have an active cart?" (point
-- lookup by hashed token on every guest request) -- and doubles as the
-- business rule "at most one ACTIVE cart per guest token" (database-design
-- §3.1, rule 1). Same partial rationale as uq_carts_customer_active above.
CREATE UNIQUE INDEX uq_carts_guest_token_active
    ON carts (guest_token_hash)
    WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- 2. Table: cart_items
--    Child rows of carts (one-to-many, ON DELETE CASCADE) -- owned entities
--    within the Cart aggregate, exactly like user_addresses under users in
--    V001. product_id is a cross-context, by-ID-only reference to catalog
--    (ON DELETE RESTRICT, database-design §3.6 rule 6); the line never joins
--    to products for its display data -- product_name/unit_price/currency
--    are a point-in-time ProductSnapshot copied at add-to-cart time
--    (database-design §3.2, domain-model §2/§3) and are immune to later
--    catalog changes.
--    PK is bigint identity -- internal child row, never client-addressed as
--    an aggregate root (ADR-0002 ID conventions).
-- ---------------------------------------------------------------------------
CREATE TABLE cart_items (
    id            bigint        GENERATED ALWAYS AS IDENTITY,
    cart_id       uuid          NOT NULL,
    product_id    uuid          NOT NULL,

    -- ProductSnapshot (domain-model §2): name + price copied at add time.
    -- Never re-derived via a join to products -- catalog changes must never
    -- silently mutate an existing cart line (database-design §3.2).
    product_name  text          NOT NULL,
    unit_price    numeric(19,4) NOT NULL,
    currency      char(3)       NOT NULL,

    quantity      int           NOT NULL,

    -- Set when the referenced product is later retired (rule 10). Flagged
    -- via a read-time catalog-port check in v1 (domain-model §4:
    -- ProductRetiredEvent has no persistent-write subscriber yet); the
    -- column exists now per the database-design §2 ERD so the flag has a
    -- place to live once/if that check starts persisting its result,
    -- without a follow-up migration. Defaults false; never drives a CHECK
    -- (whether a line is purchasable is re-validated at checkout regardless
    -- of this flag's freshness).
    unavailable   boolean       NOT NULL DEFAULT false,

    created_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,

    CONSTRAINT ck_cart_items_quantity
        CHECK (quantity > 0),
    CONSTRAINT ck_cart_items_unit_price
        CHECK (unit_price >= 0),
    CONSTRAINT ck_cart_items_currency
        CHECK (char_length(currency) = 3)
);

COMMENT ON TABLE  cart_items IS
    'One line per product within a cart. Deleted automatically when the '
    'parent cart row is deleted (CASCADE). product_name/unit_price/currency '
    'are a ProductSnapshot frozen at add-to-cart time -- never re-derived '
    'from products. Per-line quantity cap on merge (rule 1) is an '
    'application-level rule (cart use case), not a DB CHECK, since the cap '
    'value is business policy, not a fixed domain invariant -- same split as '
    'user_addresses.is_default uniqueness in V001.';
COMMENT ON COLUMN cart_items.product_id IS
    'By-ID reference to catalog''s products.id (cross-context FK, '
    'database-design §3.6). ON DELETE RESTRICT: products are never deleted '
    '(retirement is products.status = ''RETIRED''), so this FK never actually '
    'blocks a delete in practice -- it is the DB-level backstop anyway.';
COMMENT ON COLUMN cart_items.product_name IS
    'Snapshot of the product name at the moment this line was added. '
    'Immune to later renames in products (database-design §3.2).';
COMMENT ON COLUMN cart_items.unit_price IS
    'Snapshot of the product''s unit price at the moment this line was '
    'added. numeric(19,4) per the Money convention -- always paired with '
    'currency, never a bare float. Immune to later price changes in '
    'products.base_price.';
COMMENT ON COLUMN cart_items.currency IS
    'ISO 4217 alpha-3 currency code paired with unit_price. CHECK constraint '
    'enforces the fixed 3-character length.';
COMMENT ON COLUMN cart_items.quantity IS
    'Must be positive (ck_cart_items_quantity). Carts MAY exceed available '
    'stock (rule 4) -- no FK/lock against stock_items here; oversell '
    'prevention happens later, at checkout reservation time (inventory '
    'context).';
COMMENT ON COLUMN cart_items.unavailable IS
    'True once the referenced product is known to be retired (rule 10). '
    'See table comment / idx_cart_items_product_id for how this is kept '
    'current in v1.';

-- Query served: upsert-style add-to-cart -- "add the same product to this
-- cart again" increments the existing line's quantity instead of inserting
-- a duplicate (ON CONFLICT (cart_id, product_id) DO UPDATE). Leading column
-- also serves the ordinary cart -> items join (load all lines for a cart).
CREATE UNIQUE INDEX uq_cart_items_cart_product
    ON cart_items (cart_id, product_id);

-- Query served: (a) the RESTRICT check when a delete is ever attempted
-- against products.id; (b) "which cart lines reference product X" -- the
-- scan a product-retirement flow (rule 10) uses to flag/refresh affected
-- lines, run by product_id rather than by cart.
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);

-- ---------------------------------------------------------------------------
-- 3. Least-privilege grants for ecommerce_app
--    Role already exists (created idempotently in V001); no CREATE ROLE
--    here. Schema-level USAGE ON SCHEMA public was already granted in V001
--    and is not scoped per-table, so it is not repeated.
-- ---------------------------------------------------------------------------

-- carts: application performs SELECT / INSERT / UPDATE only. DELETE is
-- intentionally withheld -- a cart's lifecycle is a status change (ACTIVE ->
-- MERGED/ORDERED/ABANDONED), never a DELETE, same rule and same DB-level
-- backstop as products in V002.
GRANT SELECT, INSERT, UPDATE ON carts TO ecommerce_app;

-- cart_items: full CRUD permitted -- fully app-managed lines within the Cart
-- aggregate (add line, change quantity, remove line, clear cart), same as
-- user_addresses under users / product_images under products.
GRANT SELECT, INSERT, UPDATE, DELETE ON cart_items TO ecommerce_app;

-- bigint identity sequences created in this migration (cart_items.id).
-- V001's blanket GRANT USAGE ON ALL SEQUENCES IN SCHEMA public only covered
-- sequences that existed at the time V001 ran; it does not retroactively
-- cover sequences created by later migrations, so it is replicated here
-- (same pattern V002 followed for product_images.id).
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ecommerce_app;

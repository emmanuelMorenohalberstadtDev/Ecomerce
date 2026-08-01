-- =============================================================================
-- V004__create_inventory_schema.sql
-- Context  : inventory
-- Purpose  : Bootstrap the inventory schema.
--            Creates: stock_items, stock_reservations, stock_reservation_lines,
--                      stock_movements.
--            Grants  : ecommerce_app role with least-privilege surface.
--            Trigger : reuses set_updated_at() defined in V001 for
--                      stock_items.updated_at.
--
-- Does NOT touch: users, user_addresses, refresh_token_families,
-- refresh_tokens, password_reset_tokens, admin_audit_log (V001), categories,
-- products, product_images (V002), carts, cart_items (V003), or any other
-- prior-migration object. stock_items.product_id, stock_reservation_lines
-- .product_id, and stock_movements.product_id reference products(id) (V002)
-- as cross-context FKs only (database-design §3.6) -- no existing table is
-- altered here.
--
-- checkout context does not exist yet (it has no migration). Per
-- database-design §1/§2, stock_reservations conceptually relates to a
-- CheckoutSession (and, later, an Order), but no stub `checkout_sessions` or
-- `orders` table is created here to satisfy that -- see §2 below for how
-- checkout_session_id is handled, and the report-back notes for the
-- order_id column that appears in database-design.md's ERD but is
-- deliberately NOT added in this migration.
--
-- Safety analysis
-- ---------------
-- Lock profile  : Four brand-new, empty tables (stock_items,
--                 stock_reservations, stock_reservation_lines,
--                 stock_movements). ACCESS EXCLUSIVE locks are taken only on
--                 these new relations as they are created. The FKs from
--                 stock_items.product_id, stock_reservation_lines.product_id,
--                 and stock_movements.product_id to the existing, already-
--                 populated products table take only a brief ROW SHARE lock
--                 on products to validate the constraint -- it does not block
--                 reads/writes on products, and there is no pre-existing data
--                 in any table created here to validate against (all four are
--                 created empty in this same migration), so FK validation is
--                 effectively instant. No CONCURRENTLY needed for any index
--                 below -- every index here is built on a table containing
--                 zero rows at migration time.
-- Destructive?  : No. Greenfield creation only; no ALTER/DROP of existing
--                 objects.
-- Backfill?     : No data exists in these tables yet.
-- Forward-fix   : N/A -- no previous version of these tables to break.
--                 Purely additive from the V001+V002+V003 deployed app's
--                 point of view. When the checkout context lands, the FK
--                 `stock_reservations.checkout_session_id -> checkout_sessions
--                 (id)` is added via a new ALTER TABLE migration (expand
--                 step) -- this migration deliberately leaves the column
--                 unconstrained so that step never has to touch existing
--                 reservation rows.
-- Rollback      : DROP TABLE stock_movements, stock_reservation_lines,
--                 stock_reservations, then stock_items (reverse FK dependency
--                 order). Dev only; production follows
--                 expand-migrate-contract -- a new forward version, never an
--                 edit of this file.
-- Testcontainer : Verified from clean (V001 -> V002 -> V003 -> V004 on an
--                 empty database) and from previous version (schema at V003,
--                 then apply V004) per the flyway skill's mandatory two-path
--                 test rule.
-- Applied       : 2026-08-01  (append-only -- never modify after first apply)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Table: stock_items
--    Aggregate root of the inventory bounded context for stock levels
--    (database-design §1, §2; domain-model §2 `StockItem`). Identifying 1:1
--    relationship with products -- product_id IS the primary key, no
--    surrogate (database-design §4: "PK = product_id uuid ... no surrogate").
--    quantity_available is the row the atomic conditional decrement targets
--    for oversell prevention (rule 6, database-design §3.4):
--      UPDATE stock_items SET quantity_available = quantity_available - :n
--        WHERE product_id = :p AND quantity_available >= :n
--    Zero rows updated = shortage for that line (rule 4). The CHECK below is
--    the backstop; the row lock lasts only the statement.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_items (
    product_id          uuid        NOT NULL,

    quantity_available  int         NOT NULL,

    -- Optimistic-locking column (database-design §4, §7). Maps to @Version
    -- in JPA; concurrent updates raise OptimisticLockException -> 409. Not
    -- itself a business-rule guard -- purely concurrency control. Same
    -- convention as carts.version (V003). In practice, the oversell-critical
    -- path uses the conditional UPDATE above (row-count success signal), not
    -- load-modify-save, so this column mainly protects admin adjustment
    -- flows that do load-modify-save.
    version              bigint      NOT NULL DEFAULT 0,

    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_stock_items PRIMARY KEY (product_id),

    -- Reference data (products) is never cascade-deleted (database-design §6
    -- rule 6). A product with a stock row cannot be deleted; retirement is
    -- products.status = 'RETIRED', never a DELETE (matches cart_items
    -- .product_id / product_images.product_id conventions).
    CONSTRAINT fk_stock_items_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,

    CONSTRAINT ck_stock_items_quantity_available
        CHECK (quantity_available >= 0),
    CONSTRAINT ck_stock_items_version
        CHECK (version >= 0)
);

COMMENT ON TABLE  stock_items IS
    'One row per product tracking available stock quantity (domain-model §2 '
    'StockItem). PK = product_id -- identifying 1:1 relationship with '
    'products, no surrogate key (database-design §4). This is the row the '
    'atomic conditional decrement targets for oversell prevention (rule 6): '
    'UPDATE ... SET quantity_available = quantity_available - :n WHERE '
    'product_id = :p AND quantity_available >= :n. Every change also inserts '
    'a stock_movements ledger row in the same transaction (database-design '
    '§3.4).';
COMMENT ON COLUMN stock_items.product_id IS
    'By-ID reference to catalog''s products.id, and simultaneously this '
    'table''s own primary key (identifying relationship, no surrogate id). '
    'ON DELETE RESTRICT: products are never deleted (retirement is a status '
    'change), so this FK never actually blocks a delete in practice -- it is '
    'the DB-level backstop anyway, matching the products/cart_items pattern.';
COMMENT ON COLUMN stock_items.quantity_available IS
    'Units currently available to reserve. NOT NULL, no DEFAULT -- every '
    'stock_items row must be created with an explicit initial quantity '
    '(fail-loudly convention, same rationale as product_images.display_order '
    'in V002). CHECK (>= 0) is the backstop for the conditional-decrement '
    'oversell guard (rule 6); it must never go negative even under a bug.';
COMMENT ON COLUMN stock_items.version IS
    'Optimistic-locking counter (database-design §7), mapped as JPA @Version. '
    'The oversell-critical reserve path uses a conditional UPDATE with its '
    'own row-count success signal (see table comment) rather than '
    'load-modify-save, so this column primarily protects other mutation '
    'paths (e.g. admin manual stock correction) that do load-modify-save.';

-- Trigger: keep updated_at synchronised on every UPDATE row.
-- Reuses set_updated_at(), defined once in V001; not redefined here.
CREATE TRIGGER trg_stock_items_updated_at
    BEFORE UPDATE ON stock_items
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- No additional index needed: product_id is already the primary key, so
-- point lookup/decrement by product_id (the only query pattern against this
-- table) is already served by the PK index. Adding another index here would
-- be speculative (indexing skill rule) -- there is no other query pattern to
-- serve.

-- ---------------------------------------------------------------------------
-- 2. Table: stock_reservations
--    Aggregate root of a checkout-time stock hold (database-design §1, §2;
--    domain-model §2 `StockReservation`). HELD -> COMMITTED (payment
--    succeeds, rule 6) or HELD -> RELEASED (checkout failure/expiry, order
--    cancellation -> restock, rule 7). ID is UUIDv7, generated
--    application-side (ADR-0002 + security §6a, database-design §4), same
--    convention as carts.id/products.id: no DEFAULT on id -- a missing id
--    fails loudly instead of silently falling back to a DB-generated value.
--
--    checkout_session_id is a PLAIN uuid column with NO foreign key. The
--    checkout bounded context has no migration yet (database-design §1 lists
--    `checkout_sessions` as owned by checkout, but that table does not exist
--    in this database). Creating a stub `checkout_sessions` table here would
--    let the inventory migration silently define another context's aggregate
--    root -- out of scope for database-engineer's inventory mandate and
--    explicitly disallowed for this migration. When the checkout context's
--    own migration creates `checkout_sessions`, a FOLLOW-UP migration adds:
--        ALTER TABLE stock_reservations
--            ADD CONSTRAINT fk_stock_reservations_checkout_session
--            FOREIGN KEY (checkout_session_id)
--            REFERENCES checkout_sessions (id) ON DELETE RESTRICT;
--    That ALTER is an expand step only (validates existing data, does not
--    drop/rename anything), consistent with expand-migrate-contract.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_reservations (
    id                    uuid        NOT NULL,

    -- No FK yet -- see table comment above. Column type/name are chosen to
    -- match the eventual checkout_sessions.id (uuid, UUIDv7) exactly, so the
    -- follow-up ALTER TABLE ... ADD CONSTRAINT needs no type change.
    checkout_session_id   uuid        NOT NULL,

    -- HELD / COMMITTED / RELEASED (domain-model §2, ReservationStatus).
    -- text + CHECK, matching carts.status / products.status convention.
    status                text        NOT NULL DEFAULT 'HELD',

    -- Payment-window deadline (rule 5/8), populated at creation for HELD
    -- reservations. Nullable to accommodate a reservation created without a
    -- payment-window deadline (e.g. a future admin-initiated hold) without a
    -- schema change; every checkout-originated reservation in v1 always sets
    -- this at INSERT time.
    expires_at            timestamptz,

    created_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_stock_reservations PRIMARY KEY (id),

    CONSTRAINT ck_stock_reservations_status
        CHECK (status IN ('HELD', 'COMMITTED', 'RELEASED')),

    -- database-design §3.3: "Reservation expires_at > created_at." Expressed
    -- as IS NULL OR ... to accommodate the nullable case above.
    CONSTRAINT ck_stock_reservations_expiry
        CHECK (expires_at IS NULL OR expires_at > created_at)
);

COMMENT ON TABLE  stock_reservations IS
    'Reservation header: a temporary hold on stock created at checkout '
    '(domain-model §2 StockReservation). HELD -> COMMITTED (payment '
    'succeeds) or HELD -> RELEASED (failure/expiry/cancel, rule 7). Never '
    'deleted -- lifecycle is a status change, same DB-level backstop pattern '
    '(withheld DELETE grant, §5) as carts/products. checkout_session_id has '
    'no FK yet -- see header comment for the planned follow-up ALTER once '
    'the checkout context''s migration creates checkout_sessions.';
COMMENT ON COLUMN stock_reservations.id IS
    'UUIDv7 generated by the application before INSERT (ADR-0002). '
    'No DB default -- omitting id in an INSERT is a programmer error, not a '
    'silent fallback.';
COMMENT ON COLUMN stock_reservations.checkout_session_id IS
    'By-ID reference to the checkout context''s CheckoutSession. '
    'Intentionally NOT a foreign key in this migration -- the checkout '
    'bounded context has no table yet. A follow-up migration adds '
    'fk_stock_reservations_checkout_session once checkout_sessions exists '
    '(see file header). Until then this column is validated only at the '
    'application layer.';
COMMENT ON COLUMN stock_reservations.status IS
    'HELD (default), COMMITTED, or RELEASED. Transitions are a domain '
    'concern (inventory use cases); this CHECK only guards the set of legal '
    'values, not the transition graph.';
COMMENT ON COLUMN stock_reservations.expires_at IS
    'Payment-window deadline (rules 5, 8). Populated at creation for every '
    'checkout-originated (HELD) reservation. Nullable at the schema level '
    '(see table comment); idx_reservations_expiry_held below is the sweep '
    'index the expiry job scans.';

-- Query served: payment-window expiry sweep (rules 5, 8) -- "find all HELD
-- reservations whose deadline has passed". Partial index limited to the
-- HELD subset keeps it tiny; COMMITTED/RELEASED reservations (the majority
-- over time) never appear in it (database-design §5).
CREATE INDEX idx_reservations_expiry_held
    ON stock_reservations (expires_at)
    WHERE status = 'HELD';

-- ---------------------------------------------------------------------------
-- 3. Table: stock_reservation_lines
--    Child rows of stock_reservations (one-to-many, ON DELETE CASCADE) --
--    owned entities within the StockReservation aggregate (domain-model §2
--    `ReservedLine`), exactly like cart_items under carts in V003. Rows are
--    written once when the reservation is created and never individually
--    updated afterwards -- the reservation header's status carries the
--    lifecycle, not the lines (see grants, §5).
--    PK is bigint identity -- internal child row, never client-addressed as
--    an aggregate root (ADR-0002 ID conventions).
-- ---------------------------------------------------------------------------
CREATE TABLE stock_reservation_lines (
    id              bigint      GENERATED ALWAYS AS IDENTITY,
    reservation_id  uuid        NOT NULL,
    product_id      uuid        NOT NULL,
    quantity        int         NOT NULL,

    CONSTRAINT pk_stock_reservation_lines PRIMARY KEY (id),

    CONSTRAINT fk_stock_reservation_lines_reservation
        FOREIGN KEY (reservation_id) REFERENCES stock_reservations (id) ON DELETE CASCADE,

    -- Cross-context, by-ID-only reference to catalog (database-design §3.6
    -- rule 6) -- RESTRICT, matching cart_items.product_id / stock_items
    -- .product_id.
    CONSTRAINT fk_stock_reservation_lines_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,

    CONSTRAINT ck_stock_reservation_lines_quantity
        CHECK (quantity > 0)
);

COMMENT ON TABLE  stock_reservation_lines IS
    'Reserved quantity per product under one reservation (domain-model §2 '
    'ReservedLine). Deleted automatically when the parent reservation row is '
    'deleted (CASCADE) -- in practice reservations are never deleted (status '
    'transition only, §2 above), so this CASCADE exists for schema '
    'correctness/dev-cleanup rather than a routine production path, same '
    'note as product_images in V002. Lines are inserted once at reservation '
    'creation and never updated (§5 grants: INSERT + SELECT only).';
COMMENT ON COLUMN stock_reservation_lines.product_id IS
    'By-ID reference to catalog''s products.id (cross-context FK, '
    'database-design §3.6). ON DELETE RESTRICT: products are never deleted, '
    'so this FK never actually blocks a delete in practice -- it is the '
    'DB-level backstop anyway.';
COMMENT ON COLUMN stock_reservation_lines.quantity IS
    'Units reserved for this product under the parent reservation. Must be '
    'positive (ck_stock_reservation_lines_quantity).';

-- Query served: "load all lines for reservation X" -- the scan the
-- commit/release flow uses to walk every line of a reservation and apply
-- the corresponding stock_items decrement/restock + stock_movements ledger
-- row. Leading column also covers the CASCADE delete path. Not enumerated in
-- database-design §5's table (that list is not exhaustive -- see its
-- closing note: FK child indexes are "reviewed per migration, not
-- blanket-applied"); added here on the same real-query basis as
-- idx_cart_items_product_id / idx_product_images_product in prior
-- migrations.
CREATE INDEX idx_stock_reservation_lines_reservation
    ON stock_reservation_lines (reservation_id);

-- Query served: (a) the RESTRICT check when a delete is ever attempted
-- against products.id; (b) admin diagnostic "which reservations currently
-- hold stock for product X". Same rationale as idx_cart_items_product_id in
-- V003.
CREATE INDEX idx_stock_reservation_lines_product
    ON stock_reservation_lines (product_id);

-- ---------------------------------------------------------------------------
-- 4. Table: stock_movements
--    Append-only ledger of every stock change: reserve, commit, release,
--    admin adjustment (database-design §1, §3.4/§3.7). Two-level enforcement,
--    same pattern as admin_audit_log (V001 §8):
--      DB level  -- ecommerce_app role: INSERT + SELECT only (see §5 grants).
--      App level -- StockMovementRepository exposes insert/read only;
--                   ArchUnit asserts this (backend-lead concern).
--    Every stock_items change inserts a row here in the same transaction
--    (rule 4, database-design §3.4). Retention >= 1 year, no purge path in
--    v1, matching admin_audit_log (ADR required to change this).
-- ---------------------------------------------------------------------------
CREATE TABLE stock_movements (
    id              bigint       GENERATED ALWAYS AS IDENTITY,

    -- Cross-context, by-ID-only reference to catalog (database-design §3.6
    -- rule 6) -- RESTRICT, matching stock_items.product_id.
    product_id      uuid         NOT NULL,

    -- RESERVE (checkout hold decrements available), COMMIT (payment success,
    -- stock decremented for good -- no quantity change vs RESERVE, recorded
    -- for traceability), RELEASE (checkout failure/expiry/order cancel ->
    -- restock), ADMIN_ADJUSTMENT (manual correction, rule per domain-model
    -- §1 inventory "Admin stock adjustments live here (audited)").
    -- text + CHECK, matching every other status/type column convention.
    movement_type   text         NOT NULL,

    -- Signed change applied to stock_items.quantity_available by this row:
    -- negative for RESERVE, positive for RELEASE/restock, positive or
    -- negative for ADMIN_ADJUSTMENT. COMMIT rows record 0 (no quantity
    -- change -- the decrement already happened at RESERVE time; COMMIT only
    -- makes the reservation's hold permanent) -- so the CHECK below allows
    -- zero, unlike a strict non-zero delta.
    quantity_delta  int          NOT NULL,

    -- Soft reference: intentionally no FK to stock_reservations.id, same
    -- rationale as admin_audit_log.actor_id having no FK to users (V001 §8)
    -- -- ADMIN_ADJUSTMENT rows have no originating reservation at all, and a
    -- ledger row must never be blocked or orphaned by reservation lifecycle.
    reservation_id  uuid,

    -- Soft reference to the acting admin for ADMIN_ADJUSTMENT rows; NULL for
    -- system-driven rows (RESERVE/COMMIT/RELEASE triggered by the checkout
    -- orchestration itself, domain-model §5 Actor = system). No FK, same
    -- survives-user-deletion rationale as admin_audit_log.actor_id.
    actor_id        uuid,

    -- Free-form context, primarily populated for ADMIN_ADJUSTMENT (why the
    -- correction was made). Nullable -- system-driven rows are already fully
    -- explained by movement_type + reservation_id.
    reason          text,

    created_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stock_movements PRIMARY KEY (id),

    CONSTRAINT fk_stock_movements_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,

    CONSTRAINT ck_stock_movements_type
        CHECK (movement_type IN ('RESERVE', 'COMMIT', 'RELEASE', 'ADMIN_ADJUSTMENT'))
);

COMMENT ON TABLE  stock_movements IS
    'Append-only ledger of every stock change: reserve/commit/release/admin '
    'adjust (domain-model §1 inventory; database-design §3.4/§3.7). '
    'ecommerce_app role has INSERT + SELECT only -- no UPDATE or DELETE '
    '(same enforcement pattern as admin_audit_log, V001 §8). Retention >= 1 '
    'year. Any purge/archival policy requires an ADR and product-owner '
    'sign-off.';
COMMENT ON COLUMN stock_movements.movement_type IS
    'RESERVE, COMMIT, RELEASE, or ADMIN_ADJUSTMENT. text + CHECK -- adding a '
    'value is ALTER TABLE (cheap), not CREATE TYPE + dependency juggling, '
    'matching every other status/type column in this schema.';
COMMENT ON COLUMN stock_movements.quantity_delta IS
    'Signed change applied to stock_items.quantity_available by this event. '
    'Negative for RESERVE, positive for RELEASE (restock, rule 7); COMMIT '
    'rows record 0 (the decrement already happened at RESERVE time -- COMMIT '
    'only makes the hold permanent, no further quantity change). '
    'ADMIN_ADJUSTMENT may be positive or negative.';
COMMENT ON COLUMN stock_movements.reservation_id IS
    'Soft reference to the originating stock_reservations.id for '
    'RESERVE/COMMIT/RELEASE rows. No FK -- deliberately survives reservation '
    'lifecycle and stays NULL for ADMIN_ADJUSTMENT rows, which have no '
    'originating reservation (same no-FK rationale as admin_audit_log '
    '.actor_id in V001).';
COMMENT ON COLUMN stock_movements.actor_id IS
    'UUID of the acting admin for ADMIN_ADJUSTMENT rows; NULL for '
    'system-driven rows (RESERVE/COMMIT/RELEASE triggered by the checkout '
    'orchestration itself, domain-model §5 Actor = system). No FK -- the '
    'ledger must outlive the user row, same rationale as admin_audit_log '
    '.actor_id.';
COMMENT ON COLUMN stock_movements.reason IS
    'Free-form context for the movement, primarily populated on '
    'ADMIN_ADJUSTMENT rows to record why a manual correction was made. '
    'Credentials/PII must never be written here (security §2.8 log-scrubbing '
    'rules apply the same as admin_audit_log.details).';

-- Query served: stock ledger per product (admin review screen, database-design §5).
CREATE INDEX idx_stock_movements_product
    ON stock_movements (product_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 5. Least-privilege grants for ecommerce_app
--    Role already exists (created idempotently in V001); no CREATE ROLE
--    here. Schema-level USAGE ON SCHEMA public was already granted in V001
--    and is not scoped per-table, so it is not repeated.
-- ---------------------------------------------------------------------------

-- stock_items: application performs SELECT / INSERT / UPDATE only. DELETE is
-- intentionally withheld -- a stock row's lifecycle tracks its product 1:1,
-- and products are never deleted (same DB-level backstop pattern as products
-- in V002 / carts in V003).
GRANT SELECT, INSERT, UPDATE ON stock_items TO ecommerce_app;

-- stock_reservations: application performs SELECT / INSERT / UPDATE only
-- (status transitions HELD -> COMMITTED / RELEASED are UPDATEs). DELETE is
-- intentionally withheld -- a reservation's lifecycle is a status change,
-- never a DELETE, same rule and backstop as carts/products.
GRANT SELECT, INSERT, UPDATE ON stock_reservations TO ecommerce_app;

-- stock_reservation_lines: INSERT + SELECT only. Lines are written once when
-- the reservation is created and never individually updated or deleted --
-- the parent reservation's status carries the lifecycle (commit/release
-- act on stock_items + stock_movements, not on these rows). Withholding
-- UPDATE/DELETE here is a deliberate narrower grant than the cart_items/
-- product_images "fully app-managed child" pattern: unlike a cart line, a
-- reservation line is immutable once the reservation exists (closer in
-- spirit to order_items' expected immutability under rule 3).
GRANT SELECT, INSERT ON stock_reservation_lines TO ecommerce_app;

-- stock_movements: INSERT and SELECT ONLY. UPDATE and DELETE are permanently
-- denied at the DB level (database-design §3.7). This is the second
-- enforcement layer; the application layer (StockMovementRepository)
-- enforces the same contract in code -- identical pattern to
-- admin_audit_log in V001 §9.
GRANT SELECT, INSERT ON stock_movements TO ecommerce_app;

-- bigint identity sequences created in this migration
-- (stock_reservation_lines.id, stock_movements.id). V001's blanket GRANT
-- USAGE ON ALL SEQUENCES IN SCHEMA public only covered sequences that
-- existed at the time V001 ran; it does not retroactively cover sequences
-- created by later migrations, so it is replicated here (same pattern V002/
-- V003 followed).
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ecommerce_app;

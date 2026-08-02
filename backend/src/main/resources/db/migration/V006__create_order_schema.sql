-- =============================================================================
-- V006__create_order_schema.sql
-- Context  : order
-- Purpose  : Bootstrap the order schema.
--            Creates: orders, order_items, order_status_history.
--            Grants  : ecommerce_app role with least-privilege surface,
--                      including the append-only grant for
--                      order_status_history (database-design §3.7).
--            Trigger : reuses set_updated_at() defined in V001 for
--                      orders.updated_at.
--
-- Does NOT touch: users, categories, products, product_images, carts,
-- cart_items (V001-V003), stock_items, stock_reservations,
-- stock_reservation_lines, stock_movements (V004), or checkout_sessions
-- (V005). orders.customer_id references users(id) (V001),
-- orders.reservation_id references stock_reservations(id) (V004), and
-- order_items.product_id references products(id) (V002) as cross-context
-- FKs only (database-design §3.6) -- no existing table is altered here.
--
-- Two follow-ups are explicitly OUT OF SCOPE for this migration (matching
-- the precedent V004/V005 already set for symmetric gaps):
--   1. checkout_sessions.order_id -- V005's header already documents the
--      planned follow-up ALTER TABLE checkout_sessions ADD COLUMN order_id
--      + FK once `orders` exists. This migration does not perform that
--      ALTER; it is a separate forward migration, per the "only add this
--      one file" mandate for this change.
--   2. stock_reservations.order_id -- V004's header explicitly says this
--      ERD-shown column is deliberately NOT added there, and restates that
--      order references the reservation, not the other way around. Still
--      out of scope here; not added by this migration either.
--
-- Safety analysis
-- ---------------
-- Lock profile  : Three brand-new, empty tables (orders, order_items,
--                 order_status_history). ACCESS EXCLUSIVE locks are taken
--                 only on these new relations as they are created. The FKs
--                 from orders.customer_id -> users(id),
--                 orders.reservation_id -> stock_reservations(id), and
--                 order_items.product_id -> products(id) to existing,
--                 already-populated tables take only a brief ROW SHARE lock
--                 on those tables to validate the constraint -- it does not
--                 block reads/writes on them, and there is no pre-existing
--                 data in any table created here to validate against (all
--                 three are created empty in this same migration), so FK
--                 validation is effectively instant. No CONCURRENTLY needed
--                 for any index below -- every index here is built on a
--                 table containing zero rows at migration time.
-- Destructive?  : No. Greenfield creation only; no ALTER/DROP of existing
--                 objects.
-- Backfill?     : No data exists in these tables yet.
-- Forward-fix   : N/A -- no previous version of these tables to break.
--                 Purely additive from the V001-V005 deployed app's point of
--                 view. See header note above for the two planned follow-up
--                 ALTERs this migration deliberately leaves for later
--                 (checkout_sessions.order_id; stock_reservations.order_id
--                 stays permanently out of scope per V004).
-- Rollback      : DROP TABLE order_status_history, order_items, then orders
--                 (reverse FK dependency order). Dev only; production
--                 follows expand-migrate-contract -- a new forward version,
--                 never an edit of this file.
-- Testcontainer : Verified from clean (V001 -> V002 -> V003 -> V004 -> V005
--                 -> V006 on an empty database) and from previous version
--                 (schema at V005, then apply V006) per the flyway skill's
--                 mandatory two-path test rule.
-- Applied       : 2026-08-01  (append-only -- never modify after first apply)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Table: orders
--    Aggregate root of the order bounded context (database-design §1, §2;
--    domain-model §2 `Order`). Immutable purchase snapshot: totals frozen at
--    placement time (rule 3, database-design §3.2), a one-directional status
--    machine PLACED -> PAID -> CONFIRMED -> SHIPPED -> DELIVERED with
--    branches PLACED -> FAILED and PLACED|PAID|CONFIRMED -> CANCELLED
--    (domain-model §1 order), and at most one coupon code (rule 9). ID is
--    UUIDv7, generated application-side (ADR-0002 + security §6a,
--    database-design §4), same convention as carts.id/products.id/
--    stock_reservations.id/checkout_sessions.id: no DEFAULT on id -- a
--    missing id fails loudly instead of silently falling back to a DB-
--    generated value.
--
--    reservation_id decision (ADR-0004, this migration's judgment call):
--    NOT NULL, not nullable as ADR-0004's prose literally says. Reasoning:
--    ADR-0004 item 4 makes `Order` hold a "nullable ReservationId" at the
--    *domain/Java* type level, but that nullability is a general OO
--    modeling choice for the class diagram (`ReservationId? reservation`,
--    domain-model §3), not a statement that any v1 code path actually
--    produces an order without one. ADR-0004's own Context section states
--    plainly: "Every order in v1 is created from that event [
--    CheckoutAwaitingPaymentEvent], which always carries a non-null
--    reservationId" -- there is exactly one order-creation path in v1
--    (PlaceOrderFromCheckoutUseCase's AFTER_COMMIT listener), and
--    CheckoutAwaitingPaymentEvent's reservationId is non-nullable by
--    construction (it is the id of a reservation that checkout itself just
--    created synchronously, ADR-0003). A schema that allowed NULL here would
--    let a future application bug silently persist an order with no
--    reservation to commit/release against -- exactly the kind of state the
--    "integrity in the database, not just the app" principle exists to rule
--    out. If a genuine no-reservation order-creation path is ever added
--    (e.g. a future admin-created manual order), that is a new capability
--    requiring its own ADR; relaxing NOT NULL to nullable at that point is a
--    trivial, non-blocking expand step (`ALTER TABLE orders ALTER COLUMN
--    reservation_id DROP NOT NULL`), so starting strict costs nothing today.
--    Also UNIQUE (uq_orders_reservation, see indexes below): a reservation
--    is committed/released by at most one order -- this is a real
--    integrity rule (one reservation, one order) beyond what database-design
--    §2's ERD spells out as a CHECK, but squarely within "if a rule can be a
--    constraint, it becomes one" (database-design §3 principle). It also
--    gives a DB-level backstop against a duplicate order being created if
--    the AFTER_COMMIT listener were ever invoked twice for the same
--    reservation (fail loudly with a unique-violation, not silently double-
--    order).
--
--    coupon_code is a plain text snapshot (rule 9: max one coupon per
--    order), not a foreign key -- consistent with database-design §9
--    friction note 1: the coupon's public identity is its SecureRandom
--    `code`, not `coupons.id`, and the `promotions` context (which would own
--    a `coupons` table) has no migration yet in this database. The actual
--    redemption relationship (coupon <-> order, cap enforcement) lives in
--    `coupon_redemptions` when the promotions context ships; this column is
--    only the display/audit snapshot on the order itself.
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id              uuid           NOT NULL,

    -- Reference data (users) is never cascade-deleted (database-design §3.6
    -- rule 6). A customer with order history cannot be deleted; deactivation
    -- is account_locked = true on users (V001), never a DELETE.
    customer_id     uuid           NOT NULL,

    -- PLACED (default) / PAID / CONFIRMED / SHIPPED / DELIVERED / FAILED /
    -- CANCELLED -- 7 values (domain-model §1 order; database-design §2 "CHECK
    -- 7 values"). text + CHECK, matching every other status column
    -- convention in this schema. Transitions are a domain concern (order use
    -- cases); this CHECK only guards the set of legal values, not the
    -- transition graph.
    status          text           NOT NULL DEFAULT 'PLACED',

    -- Snapshot totals (rule 3, database-design §2/§3.2) -- frozen at
    -- placement time from the same RecalculatedTotal checkout already
    -- confirmed and reserved against (ADR-0004 item 1). Never re-derived via
    -- a join to pricing/cart/catalog.
    items_total     numeric(19,4)  NOT NULL,
    discount_total  numeric(19,4)  NOT NULL,
    shipping_total  numeric(19,4)  NOT NULL,
    grand_total     numeric(19,4)  NOT NULL,
    currency        char(3)        NOT NULL,

    -- Snapshot of the coupon code applied at placement, if any (rule 9: max
    -- one). No FK -- see table comment above.
    coupon_code     text,

    -- By-ID reference to inventory's reservation header (ADR-0004). NOT
    -- NULL + UNIQUE -- see table comment above for the full reasoning.
    -- Never displayed to the customer (domain-model §2: "commit/release
    -- only, never displayed").
    reservation_id  uuid           NOT NULL,

    -- Optimistic-locking column (database-design §4, §7). Maps to @Version
    -- in JPA; concurrent updates (e.g. two admin actions racing a status
    -- transition) raise OptimisticLockException -> 409. Same convention as
    -- carts.version / stock_items.version / checkout_sessions.version.
    version         bigint         NOT NULL DEFAULT 0,

    -- The moment the order was placed (domain-model §2 orders ERD: "
    -- timestamptz placed_at"). Doubles as this row's creation timestamp --
    -- an orders row is only ever inserted already in PLACED status (there is
    -- no pre-placement row state), so a separate generic `created_at` would
    -- carry the exact same value under a different name. Named `placed_at`
    -- (not `created_at`) deliberately, matching database-design §2's ERD and
    -- because it is the column idx_orders_customer_placed sorts on --
    -- consumers should read it as "when this order was placed", the domain
    -- event, not "when this row happened to be written".
    placed_at       timestamptz    NOT NULL DEFAULT now(),

    -- Bumped by trg_orders_updated_at on every subsequent status transition
    -- (PAID/CONFIRMED/SHIPPED/DELIVERED/FAILED/CANCELLED). Blanket
    -- convention (database-design §4): every table gets one.
    updated_at      timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT pk_orders PRIMARY KEY (id),

    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- Cross-context, by-ID reference to inventory's reservation header.
    -- RESTRICT, matching checkout_sessions.reservation_id (V005) and every
    -- other cross-context FK in this schema -- reservations are never
    -- deleted in practice (V004), this is the same DB-level backstop
    -- pattern.
    CONSTRAINT fk_orders_reservation
        FOREIGN KEY (reservation_id) REFERENCES stock_reservations (id) ON DELETE RESTRICT,

    -- "A reservation is committed/released by at most one order" (see
    -- column comment above) -- declared inline like every other constraint
    -- in this table, matching this file's own style. Also serves as the
    -- index the RESTRICT-check on stock_reservations.id deletes and any
    -- future "which order owns reservation X" lookup would use.
    CONSTRAINT uq_orders_reservation UNIQUE (reservation_id),

    CONSTRAINT ck_orders_status
        CHECK (status IN ('PLACED', 'PAID', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'FAILED', 'CANCELLED')),

    CONSTRAINT ck_orders_items_total
        CHECK (items_total >= 0),
    CONSTRAINT ck_orders_discount_total
        CHECK (discount_total >= 0),
    CONSTRAINT ck_orders_shipping_total
        CHECK (shipping_total >= 0),
    CONSTRAINT ck_orders_grand_total
        CHECK (grand_total >= 0),
    CONSTRAINT ck_orders_currency
        CHECK (char_length(currency) = 3),

    CONSTRAINT ck_orders_version
        CHECK (version >= 0)
);

COMMENT ON TABLE  orders IS
    'Immutable purchase snapshot: totals, status machine, max one coupon '
    'code (domain-model §1/§2 Order; database-design rules 3, 9). Never '
    'deleted -- lifecycle is a status change only, enforced as the DB-level '
    'backstop by withholding DELETE at the grant level (§2 below), matching '
    'carts/stock_reservations/checkout_sessions. reservation_id is NOT NULL '
    'and UNIQUE -- see column comment for the full reasoning (ADR-0004).';
COMMENT ON COLUMN orders.id IS
    'UUIDv7 generated by the application before INSERT (ADR-0002). '
    'No DB default -- omitting id in an INSERT is a programmer error, not a '
    'silent fallback.';
COMMENT ON COLUMN orders.customer_id IS
    'Owning registered customer. ON DELETE RESTRICT: a customer with order '
    'history cannot be deleted.';
COMMENT ON COLUMN orders.status IS
    'PLACED (default) / PAID / CONFIRMED / SHIPPED / DELIVERED / FAILED / '
    'CANCELLED -- 7 values (domain-model §1: PLACED -> PAID -> CONFIRMED -> '
    'SHIPPED -> DELIVERED, plus PLACED -> FAILED and '
    'PLACED|PAID|CONFIRMED -> CANCELLED). This CHECK only guards the set of '
    'legal values -- the transition graph itself is enforced by the domain '
    '(Order.markPaid/confirm/ship/deliver/cancel/fail), not by the database.';
COMMENT ON COLUMN orders.items_total IS
    'Snapshot column (rule 3, database-design §3.2) -- sum of line totals at '
    'placement time. Never re-derived via a join.';
COMMENT ON COLUMN orders.discount_total IS
    'Snapshot column. Combined promotion + coupon discount applied at '
    'placement. CHECK (>= 0) -- a discount never goes negative (rule 9).';
COMMENT ON COLUMN orders.shipping_total IS
    'Snapshot column. Shipping fee at placement time.';
COMMENT ON COLUMN orders.grand_total IS
    'Snapshot column. items_total - discount_total + shipping_total, as '
    'confirmed by checkout at placement time -- stored, not recomputed on '
    'read.';
COMMENT ON COLUMN orders.currency IS
    'ISO 4217 alpha-3 currency code paired with every money column on this '
    'row -- one order, one currency. CHECK constraint enforces the fixed '
    '3-character length.';
COMMENT ON COLUMN orders.coupon_code IS
    'Snapshot of the coupon code applied at placement (rule 9: max one '
    'coupon per order). Nullable -- most orders carry no coupon. No FK: the '
    'coupon''s public identity is its SecureRandom code, not an internal id '
    '(database-design §9 friction note 1); the promotions context, which '
    'would own a coupons table, has no migration yet. This column is a '
    'display/audit snapshot only -- the authoritative redemption record is '
    'coupon_redemptions (coupon_id, order_id) once promotions ships.';
COMMENT ON COLUMN orders.reservation_id IS
    'By-ID reference to inventory''s stock_reservations.id, captured from '
    'CheckoutAwaitingPaymentEvent.reservationId() when the order listener '
    'creates the Order in PLACED (ADR-0004 item 4). NOT NULL: every v1 '
    'order-creation path (PlaceOrderFromCheckoutUseCase) always supplies '
    'one, sourced from an event whose reservationId is itself non-null by '
    'construction -- a nullable column here would let a DB-level state exist '
    'that no application code ever legitimately produces. UNIQUE '
    '(uq_orders_reservation): a reservation is committed/released by at most '
    'one order -- also guards against a duplicate order if the AFTER_COMMIT '
    'listener ever double-fires for the same reservation. Never displayed to '
    'the customer -- used only by MarkOrderPaidUseCase (.commit) and '
    'CancelOrderUseCase/FailOrderUseCase (.release) via order''s own '
    'ReservationPort (ADR-0004 item 3).';
COMMENT ON COLUMN orders.version IS
    'Optimistic-locking counter (database-design §7), mapped as JPA '
    '@Version. A stale concurrent status-transition UPDATE raises '
    'OptimisticLockException -> 409.';
COMMENT ON COLUMN orders.placed_at IS
    'The moment the order was placed -- also this row''s creation timestamp '
    '(an orders row is only ever inserted already in PLACED status, so the '
    'two moments always coincide; see table/column design note in the file '
    'header section above). idx_orders_customer_placed sorts on this column '
    'for order-history pagination (database-design §5).';
COMMENT ON COLUMN orders.updated_at IS
    'Bumped by trg_orders_updated_at on every subsequent status-transition '
    'UPDATE. Blanket convention (database-design §4), same as every other '
    'table in this schema.';

-- Trigger: keep updated_at synchronised on every UPDATE row.
-- Reuses set_updated_at(), defined once in V001; not redefined here.
CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Query served: order history list -- "this customer's orders, newest
-- first" (equality on customer_id + sort on placed_at DESC). INCLUDE columns
-- make it a covering index: an index-only scan serves the list page without
-- touching the heap (database-design §5, exact name/shape as specified).
-- Leading column (customer_id) also serves plain equality lookups by
-- customer and the RESTRICT-check on users deletes, so no separate
-- idx_orders_customer_id is added (would be a redundant prefix of this
-- index).
CREATE INDEX idx_orders_customer_placed
    ON orders (customer_id, placed_at DESC)
    INCLUDE (status, grand_total, currency);

-- No separate index needed for the RESTRICT-check / "which order owns
-- reservation X" lookup on reservation_id -- the uq_orders_reservation
-- UNIQUE constraint declared inline in the CREATE TABLE above already backs
-- both (Postgres creates a unique b-tree index for it automatically).

-- ---------------------------------------------------------------------------
-- 2. Table: order_items
--    Child rows of orders (one-to-many, ON DELETE CASCADE) -- owned entities
--    within the Order aggregate (domain-model §2 `OrderLine`), exactly like
--    cart_items under carts (V003) and stock_reservation_lines under
--    stock_reservations (V004). Rows are written once when the order is
--    placed and never updated afterwards -- rule 3, an order line is a
--    frozen LineSnapshot (name, unit price, qty) at placement time, never
--    re-priced, never re-reads catalog. PK is bigint identity -- internal
--    child row, never client-addressed as an aggregate root (ADR-0002 ID
--    conventions).
--
--    Carries its own product_name/unit_price/currency snapshot columns even
--    though database-design §2's ERD "key columns" box for order_items only
--    lists product_name and unit_price -- §3 rule 2's prose is explicit that
--    "cart_items and order_items carry product_name, unit_price, currency
--    copied at add/placement time", and §4's money convention is "numeric
--    (19,4) amount + char(3) currency code, always paired". The prose rule
--    and the type convention both bind here over the non-exhaustive ERD box
--    (its own header says "key columns only").
-- ---------------------------------------------------------------------------
CREATE TABLE order_items (
    id            bigint         GENERATED ALWAYS AS IDENTITY,
    order_id      uuid           NOT NULL,

    -- Cross-context, by-ID-only reference to catalog (database-design §3.6
    -- rule 6) -- RESTRICT, matching cart_items.product_id / stock_items
    -- .product_id / stock_reservation_lines.product_id. Display linkage
    -- only (domain-model §2 order "References out": "ProductId (for display
    -- linkage only)") -- no read path reconstructs price/name via this FK.
    product_id    uuid           NOT NULL,

    -- LineSnapshot (domain-model §2/§3 OrderLine.snapshot), frozen at
    -- placement time -- never re-read from products after INSERT (rule 3).
    product_name  text           NOT NULL,
    unit_price    numeric(19,4)  NOT NULL,
    currency      char(3)        NOT NULL,

    quantity      int            NOT NULL,

    CONSTRAINT pk_order_items PRIMARY KEY (id),

    -- Parent -> child inside one aggregate (database-design §3.6) -- CASCADE.
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,

    CONSTRAINT ck_order_items_unit_price
        CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_currency
        CHECK (char_length(currency) = 3),
    CONSTRAINT ck_order_items_quantity
        CHECK (quantity > 0)
);

COMMENT ON TABLE  order_items IS
    'LineSnapshot per line: name, unit price, currency, quantity at '
    'placement time (domain-model §2 OrderLine; database-design rule 3). '
    'Deleted automatically when the parent order row is deleted (CASCADE) -- '
    'in practice orders are never deleted (status transition only, see §1 '
    'grants), so this CASCADE exists for schema correctness/dev-cleanup '
    'rather than a routine production path, same note as '
    'stock_reservation_lines in V004. Lines are inserted once at order '
    'placement and never updated (§4 grants: INSERT + SELECT only -- an '
    'order line is immutable once the order exists, same rationale V004 '
    'gave stock_reservation_lines and explicitly foretold for this table).';
COMMENT ON COLUMN order_items.product_id IS
    'By-ID reference to catalog''s products.id (cross-context FK, '
    'database-design §3.6) -- display linkage only, never a price/name '
    'source after INSERT. ON DELETE RESTRICT: products are never deleted, '
    'so this FK never actually blocks a delete in practice -- it is the '
    'DB-level backstop anyway, matching every other products FK in this '
    'schema.';
COMMENT ON COLUMN order_items.product_name IS
    'Snapshot of the product''s display name at placement time (rule 3). '
    'Falls back to a placeholder ("Unavailable product " + productId) at '
    'the application layer if the product was retired in the narrow window '
    'between checkout''s reservation commit and order''s listener firing '
    '(ADR-0004 item 1) -- the database only stores whatever value the '
    'application resolved, this column has no CHECK tying it to products.';
COMMENT ON COLUMN order_items.unit_price IS
    'Snapshot of the unit price at placement time, sourced from the same '
    'RecalculatedTotal line checkout already confirmed and reserved against '
    '(ADR-0004 item 1) -- never re-derived from products.base_price.';
COMMENT ON COLUMN order_items.currency IS
    'Snapshot currency code, paired with unit_price (database-design §4). '
    'Expected to equal the parent orders.currency for every row in v1 (one '
    'order, one currency) -- stored per line anyway to match the '
    'cart_items/order_items snapshot convention (§3 rule 2) rather than an '
    'implicit join back to the parent for a value that must never change.';
COMMENT ON COLUMN order_items.quantity IS
    'Units purchased for this line. Must be positive '
    '(ck_order_items_quantity).';

-- Query served: order detail join -- "load all lines for order X"; also the
-- business rule "one line per product per order" (database-design §5, exact
-- name as specified). Leading column also covers the CASCADE delete path.
CREATE UNIQUE INDEX uq_order_items_order_product
    ON order_items (order_id, product_id);

-- Query served: (a) the RESTRICT check when a delete is ever attempted
-- against products.id; (b) admin "orders containing product X" (database-
-- design §5, exact name as specified).
CREATE INDEX idx_order_items_product_id
    ON order_items (product_id);

-- ---------------------------------------------------------------------------
-- 3. Table: order_status_history
--    Append-only transition log: from -> to, actor, timestamp (rule 12,
--    database-design §1/§2/§3.7; domain-model §2/§3 `OrderStatusTransition`).
--    Two-level enforcement, same pattern as admin_audit_log (V001) and
--    stock_movements (V004):
--      DB level  -- ecommerce_app role: INSERT + SELECT only (see §4 grants).
--      App level -- OrderStatusHistoryRepository exposes insert/read only;
--                   ArchUnit asserts this (backend-lead concern).
--    Every order status transition inserts a row here in the same
--    transaction as the Order UPDATE (rule 12). PK is bigint identity --
--    internal child row, never client-addressed as an aggregate root.
--    from_status is nullable to represent the very first row: order
--    creation itself (NULL -> PLACED) has no prior status to record, unlike
--    every subsequent transition which always has one.
-- ---------------------------------------------------------------------------
CREATE TABLE order_status_history (
    id            bigint       GENERATED ALWAYS AS IDENTITY,
    order_id      uuid         NOT NULL,

    -- NULL only for the initial PLACED row (order creation has no prior
    -- status). Every subsequent transition row has both from_status and
    -- to_status populated.
    from_status   text,
    to_status     text         NOT NULL,

    -- CUSTOMER / ADMIN / SYSTEM (domain-model §2 order "Actor
    -- (customer/admin/system)"). text + CHECK, matching every other
    -- status/type column convention in this schema.
    actor_type    text         NOT NULL,

    occurred_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_order_status_history PRIMARY KEY (id),

    -- Parent -> child inside one aggregate (database-design §3.6) -- CASCADE.
    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,

    CONSTRAINT ck_order_status_history_from_status
        CHECK (from_status IS NULL OR from_status IN
            ('PLACED', 'PAID', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_order_status_history_to_status
        CHECK (to_status IN
            ('PLACED', 'PAID', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_order_status_history_transition_changes_status
        CHECK (from_status IS NULL OR from_status <> to_status),
    CONSTRAINT ck_order_status_history_actor_type
        CHECK (actor_type IN ('CUSTOMER', 'ADMIN', 'SYSTEM'))
);

COMMENT ON TABLE  order_status_history IS
    'Append-only transition log: from -> to, actor, timestamp (rule 12, '
    'domain-model §2/§3 OrderStatusTransition). ecommerce_app role has '
    'INSERT + SELECT only -- no UPDATE or DELETE (same enforcement pattern '
    'as admin_audit_log V001 / stock_movements V004). Retention >= 1 year, '
    'no purge path in v1, matching those tables (ADR required to change '
    'this).';
COMMENT ON COLUMN order_status_history.order_id IS
    'By-ID reference to the parent order. ON DELETE CASCADE -- child row '
    'inside the Order aggregate (database-design §3.6); in practice orders '
    'are never deleted, so this CASCADE exists for schema correctness only, '
    'same note as order_items above.';
COMMENT ON COLUMN order_status_history.from_status IS
    'NULL only for the row recording order creation (no prior status); '
    'populated for every subsequent transition. CHECK constrains it to the '
    'same 7-value status set as orders.status when present.';
COMMENT ON COLUMN order_status_history.to_status IS
    'The status the order transitioned into. Always populated -- CHECK '
    'constrains it to the same 7-value status set as orders.status. This '
    'table only guards the set of legal *values*; the legal transition '
    '*graph* (e.g. PAID can never go to PLACED) is a domain concern '
    '(Order''s markPaid/confirm/ship/deliver/cancel/fail methods), not '
    'enforced here.';
COMMENT ON COLUMN order_status_history.actor_type IS
    'CUSTOMER, ADMIN, or SYSTEM (domain-model §2 Actor). No actor_id column '
    '-- database-design §2''s ERD for this table lists only actor_type, not '
    'an actor id; who specifically (which admin) is out of this table''s '
    'documented scope for v1.';
COMMENT ON COLUMN order_status_history.occurred_at IS
    'When this transition happened. idx_order_status_history_order below is '
    'the index the order-detail timeline view scans (database-design §5).';

-- Query served: order detail timeline -- "load this order's full status
-- history, in order" (database-design §5, exact name/shape as specified).
-- Leading column also covers the CASCADE delete path.
CREATE INDEX idx_order_status_history_order
    ON order_status_history (order_id, occurred_at);

-- ---------------------------------------------------------------------------
-- 4. Least-privilege grants for ecommerce_app
--    Role already exists (created idempotently in V001); no CREATE ROLE
--    here. Schema-level USAGE ON SCHEMA public was already granted in V001
--    and is not scoped per-table, so it is not repeated.
-- ---------------------------------------------------------------------------

-- orders: application performs SELECT / INSERT / UPDATE only. DELETE is
-- intentionally withheld -- an order's lifecycle is a status change, never a
-- DELETE, same rule and DB-level backstop as carts/stock_reservations/
-- checkout_sessions.
GRANT SELECT, INSERT, UPDATE ON orders TO ecommerce_app;

-- order_items: INSERT + SELECT only. Lines are written once when the order
-- is placed and never individually updated or deleted afterwards (rule 3:
-- an order line is a frozen snapshot). Same narrower-than-cart_items grant
-- shape as stock_reservation_lines (V004), which explicitly named
-- order_items as the pattern this table would follow.
GRANT SELECT, INSERT ON order_items TO ecommerce_app;

-- order_status_history: INSERT and SELECT ONLY. UPDATE and DELETE are
-- permanently denied at the DB level (database-design §3.7, rule 12). This
-- is the second enforcement layer; the application layer
-- (OrderStatusHistoryRepository) enforces the same contract in code --
-- identical pattern to admin_audit_log (V001) and stock_movements (V004).
GRANT SELECT, INSERT ON order_status_history TO ecommerce_app;

-- bigint identity sequences created in this migration (order_items.id,
-- order_status_history.id). V001's blanket
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA public only covered sequences that
-- existed at the time V001 ran; it does not retroactively cover sequences
-- created by later migrations, so it is replicated here (same pattern
-- V002/V003/V004 followed).
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ecommerce_app;

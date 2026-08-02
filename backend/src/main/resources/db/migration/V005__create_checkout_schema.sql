-- =============================================================================
-- V005__create_checkout_schema.sql
-- Context  : checkout
-- Purpose  : Bootstrap the checkout schema.
--            Creates: checkout_sessions.
--            Grants  : ecommerce_app role with least-privilege surface.
--            Trigger : reuses set_updated_at() defined in V001 for
--                      checkout_sessions.updated_at.
--
-- Does NOT touch: users, categories, products, product_images, carts,
-- cart_items (V001-V003), stock_items, stock_reservations,
-- stock_reservation_lines, stock_movements (V004), or any other
-- prior-migration object. checkout_sessions.customer_id references
-- users(id) (V001), checkout_sessions.cart_id references carts(id) (V003),
-- and checkout_sessions.reservation_id references stock_reservations(id)
-- (V004) as cross-context FKs only (database-design §3.6) -- no existing
-- table is altered here.
--
-- order context does not exist yet (it has no migration). Per domain-model
-- §2, CheckoutSession references out to an OrderId once an order is placed,
-- but no order_id column is added here and no stub `orders` table is
-- created to satisfy that -- same precedent already set by
-- stock_reservations deliberately shipping without an order_id column in
-- V004. When the order context's own migration creates `orders`, a
-- FOLLOW-UP migration adds:
--     ALTER TABLE checkout_sessions
--         ADD COLUMN order_id uuid,
--         ADD CONSTRAINT fk_checkout_sessions_order
--         FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT;
-- That ALTER is an expand step only (new nullable column + validated FK on
-- an all-NULL column, effectively instant), consistent with
-- expand-migrate-contract.
--
-- Similarly, stock_reservations.checkout_session_id (added in V004) still
-- has no FK back to this table -- wiring that constraint is a separate
-- follow-up migration (not this one, per the "only add this one file"
-- mandate for this change) and is out of scope here.
--
-- Safety analysis
-- ---------------
-- Lock profile  : One brand-new, empty table (checkout_sessions). ACCESS
--                 EXCLUSIVE lock is taken only on this new relation as it is
--                 created. The FKs from checkout_sessions.customer_id,
--                 .cart_id, and .reservation_id to the existing,
--                 already-populated users/carts/stock_reservations tables
--                 take only a brief ROW SHARE lock on those tables to
--                 validate the constraint -- it does not block reads/writes
--                 on them, and there is no pre-existing data in
--                 checkout_sessions to validate against (created empty in
--                 this same migration), so FK validation is effectively
--                 instant. No CONCURRENTLY needed for any index below --
--                 every index here is built on a table containing zero rows
--                 at migration time.
-- Destructive?  : No. Greenfield creation only; no ALTER/DROP of existing
--                 objects.
-- Backfill?     : No data exists in this table yet.
-- Forward-fix   : N/A -- no previous version of checkout_sessions to break.
--                 Purely additive from the V001-V004 deployed app's point of
--                 view. See header note above for the planned follow-up
--                 ALTER once the order context lands (order_id) and the
--                 planned follow-up ALTER on stock_reservations (reverse FK).
-- Rollback      : DROP TABLE checkout_sessions. Dev only; production follows
--                 expand-migrate-contract -- a new forward version, never an
--                 edit of this file.
-- Testcontainer : Verified from clean (V001 -> V002 -> V003 -> V004 -> V005
--                 on an empty database) and from previous version (schema at
--                 V004, then apply V005) per the flyway skill's mandatory
--                 two-path test rule.
-- Applied       : 2026-08-01  (append-only -- never modify after first apply)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Table: checkout_sessions
--    Aggregate root of the checkout bounded context (database-design §1, §2;
--    domain-model §2 `CheckoutSession`). Owns process orchestration state
--    only -- no catalog, stock, or price data is owned here (domain-model
--    §1 checkout). ID is UUIDv7, generated application-side (ADR-0002 +
--    security §6a, database-design §4), same convention as carts.id/
--    products.id/stock_reservations.id: no DEFAULT on id -- a missing id
--    fails loudly instead of silently falling back to a DB-generated value.
--
--    SessionStatus values (ADR-0003 §Decision item 3/4): PENDING_CONFIRMATION
--    (price mismatch detected, customer re-confirmation required, no
--    reservation made yet) -> AWAITING_PAYMENT (totals confirmed, stock
--    reserved, 15-minute payment window running) -> EXPIRED (payment window
--    lapsed, sweep released the reservation). Deliberately not adding
--    PAID/CANCELLED yet -- ADR-0003 scopes this change to
--    StartCheckoutUseCase / ConfirmRecalculatedTotalUseCase /
--    ExpirePaymentWindowUseCase only; those two further values arrive with
--    the payment/order hand-off work, as a new migration extending the
--    CHECK (cheap ALTER, text + CHECK convention -- database-design §4).
--
--    reservation_id is NULL while PENDING_CONFIRMATION (no reservation
--    exists yet -- StartCheckoutUseCase only reserves stock once the
--    recalculated total is confirmed, ADR-0003 context) and set once a
--    reservation is made for the AWAITING_PAYMENT transition.
--
--    Totals are a snapshot pattern (database-design §3.2), matching
--    cart_items/order_items -- checkout_sessions carries its OWN copy of
--    subtotal/discount_total/shipping_fee/grand_total/currency, computed by
--    the pricing context via PriceQuotePort (ADR-0003) at recalculation
--    time. No read path reconstructs these via a join back to pricing or
--    catalog. expected_total is the total the client/cart believed BEFORE
--    recalculation -- kept alongside so the mismatch that drives
--    PENDING_CONFIRMATION vs. proceeding straight to reservation can be
--    detected and later audited without recomputing anything.
-- ---------------------------------------------------------------------------
CREATE TABLE checkout_sessions (
    id                 uuid           NOT NULL,

    -- Checkout is customer-only, no guest path (security-architecture §3.4
    -- decision table: checkout GUEST row is 401, not permitted). Unlike
    -- carts.customer_id, this column is NOT NULL -- there is no
    -- guest_token_hash sibling here, no XOR identity constraint needed.
    customer_id        uuid           NOT NULL,

    -- Checkout reads a cart, it does not own it (domain-model §1 checkout:
    -- "only process state"). RESTRICT, not CASCADE -- a cart is never
    -- deleted out from under an in-flight checkout session.
    cart_id            uuid           NOT NULL,

    -- PENDING_CONFIRMATION (default) / AWAITING_PAYMENT / EXPIRED
    -- (ADR-0003 §Decision item 3/4; domain-model §2 SessionStatus). text +
    -- CHECK, matching every other status column convention in this schema.
    status             text           NOT NULL DEFAULT 'PENDING_CONFIRMATION',

    -- NULL while PENDING_CONFIRMATION; set once a reservation is made for
    -- the AWAITING_PAYMENT transition (see table comment). RESTRICT: a
    -- reservation with an owning checkout session cannot be deleted (moot in
    -- practice -- stock_reservations rows are never deleted, V004 -- but
    -- kept as the same DB-level backstop pattern as every other
    -- reference-data FK here).
    reservation_id     uuid,

    -- Recalculated total snapshot (own columns, not a join to pricing --
    -- database-design §3.2 snapshot pattern). Computed via PriceQuotePort
    -- (ADR-0003) at StartCheckoutUseCase / ConfirmRecalculatedTotalUseCase
    -- time.
    subtotal           numeric(19,4)  NOT NULL,
    discount_total     numeric(19,4)  NOT NULL,
    shipping_fee       numeric(19,4)  NOT NULL,
    grand_total        numeric(19,4)  NOT NULL,

    -- The total the client/cart believed BEFORE recalculation -- compared
    -- against grand_total to detect the mismatch that drives
    -- PENDING_CONFIRMATION vs. proceeding straight to reservation (ADR-0003
    -- StartCheckoutUseCase behavior). Shares the currency column below with
    -- the recalculated totals -- a session has exactly one currency, it
    -- never straddles two.
    expected_total     numeric(19,4)  NOT NULL,

    currency           char(3)        NOT NULL,

    -- Populated only once AWAITING_PAYMENT (15-minute payment window, rules
    -- 5/8). NULL while PENDING_CONFIRMATION -- no window is running until
    -- stock is actually reserved.
    payment_deadline   timestamptz,

    -- Client-generated idempotency key (api-guidelines §5): 1-255 chars,
    -- opaque to the server. Nullable -- not every checkout-context write
    -- path is required to carry one; the endpoints that must
    -- (security-architecture §1.4 case 6) enforce presence at the
    -- application layer (missing key -> 400), not via a NOT NULL here,
    -- since this same table also backs reads/transitions that are not the
    -- idempotency-guarded mutation itself.
    idempotency_key    text,

    -- Optimistic-locking column (database-design §4, §7). Maps to @Version
    -- in JPA; concurrent updates raise OptimisticLockException -> 409. Same
    -- convention as carts.version / stock_items.version.
    version            bigint         NOT NULL DEFAULT 0,

    created_at         timestamptz    NOT NULL DEFAULT now(),
    updated_at         timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT pk_checkout_sessions PRIMARY KEY (id),

    -- Reference data (users) is never cascade-deleted (database-design §3.6
    -- rule 6). A customer with an in-flight or historical checkout session
    -- cannot be deleted; deactivation is account_locked = true on users
    -- (V001), never a DELETE.
    CONSTRAINT fk_checkout_sessions_customer
        FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- Checkout reads a cart, it does not own it -- RESTRICT, not CASCADE.
    CONSTRAINT fk_checkout_sessions_cart
        FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE RESTRICT,

    -- Cross-context, by-ID reference to inventory's reservation header.
    -- RESTRICT, matching every other cross-context FK in this schema.
    CONSTRAINT fk_checkout_sessions_reservation
        FOREIGN KEY (reservation_id) REFERENCES stock_reservations (id) ON DELETE RESTRICT,

    CONSTRAINT ck_checkout_sessions_status
        CHECK (status IN ('PENDING_CONFIRMATION', 'AWAITING_PAYMENT', 'EXPIRED')),

    CONSTRAINT ck_checkout_sessions_subtotal
        CHECK (subtotal >= 0),
    CONSTRAINT ck_checkout_sessions_discount_total
        CHECK (discount_total >= 0),
    CONSTRAINT ck_checkout_sessions_shipping_fee
        CHECK (shipping_fee >= 0),
    CONSTRAINT ck_checkout_sessions_grand_total
        CHECK (grand_total >= 0),
    CONSTRAINT ck_checkout_sessions_expected_total
        CHECK (expected_total >= 0),
    CONSTRAINT ck_checkout_sessions_currency
        CHECK (char_length(currency) = 3),

    -- database-design §3.3-style rule, mirroring
    -- ck_stock_reservations_expiry (V004): a payment deadline, once set,
    -- must lie strictly after the session was created. NULL is allowed
    -- (PENDING_CONFIRMATION has no deadline yet).
    CONSTRAINT ck_checkout_sessions_payment_deadline
        CHECK (payment_deadline IS NULL OR payment_deadline > created_at),

    -- No unbounded user text (database-design §4). 255 matches the
    -- api-guidelines §5 idempotency-key contract ceiling exactly.
    CONSTRAINT ck_checkout_sessions_idempotency_key_length
        CHECK (idempotency_key IS NULL OR char_length(idempotency_key) <= 255),

    CONSTRAINT ck_checkout_sessions_version
        CHECK (version >= 0)
);

COMMENT ON TABLE  checkout_sessions IS
    'Orchestration state for the purchase process (domain-model §1/§2 '
    'CheckoutSession): recalculated totals, reservation reference, '
    '15-minute payment deadline, idempotency key. Owns no catalog/stock/ '
    'price data -- process state only. Never deleted -- lifecycle is a '
    'status change (PENDING_CONFIRMATION -> AWAITING_PAYMENT -> EXPIRED, '
    'more values to follow with payment/order work), enforced as the '
    'DB-level backstop by withholding DELETE at the grant level (§2 below), '
    'matching carts/stock_reservations.';
COMMENT ON COLUMN checkout_sessions.id IS
    'UUIDv7 generated by the application before INSERT (ADR-0002). '
    'No DB default -- omitting id in an INSERT is a programmer error, not a '
    'silent fallback.';
COMMENT ON COLUMN checkout_sessions.customer_id IS
    'Owning registered customer. NOT NULL -- checkout is customer-only, no '
    'guest path (security-architecture §3.4: checkout GUEST access is 401). '
    'ON DELETE RESTRICT: a customer with checkout history cannot be deleted.';
COMMENT ON COLUMN checkout_sessions.cart_id IS
    'By-ID reference to the cart being checked out. ON DELETE RESTRICT -- '
    'checkout reads a cart, it does not own it (domain-model §1 checkout), '
    'so a cart with an in-flight or historical checkout session cannot be '
    'deleted out from under it.';
COMMENT ON COLUMN checkout_sessions.status IS
    'PENDING_CONFIRMATION (default), AWAITING_PAYMENT, or EXPIRED '
    '(ADR-0003 §Decision item 3/4). PAID/CANCELLED are intentionally not yet '
    'added -- out of scope for the use cases this migration supports '
    '(StartCheckoutUseCase, ConfirmRecalculatedTotalUseCase, '
    'ExpirePaymentWindowUseCase); a future migration extends this CHECK when '
    'payment/order hand-off lands. Transitions are a domain concern '
    '(checkout use cases); this CHECK only guards the set of legal values, '
    'not the transition graph.';
COMMENT ON COLUMN checkout_sessions.reservation_id IS
    'By-ID reference to inventory''s stock_reservations.id. NULL while '
    'PENDING_CONFIRMATION -- no reservation is made until the recalculated '
    'total is confirmed (ADR-0003 StartCheckoutUseCase behavior); set once '
    'AWAITING_PAYMENT. ON DELETE RESTRICT: reservations are never deleted in '
    'practice (V004), this is the same DB-level backstop pattern as every '
    'other reference FK here.';
COMMENT ON COLUMN checkout_sessions.subtotal IS
    'Snapshot column (database-design §3.2) -- checkout''s own copy of the '
    'pre-discount, pre-shipping line total returned by PriceQuotePort '
    '(ADR-0003) at recalculation time. Never re-derived via a join to '
    'pricing.';
COMMENT ON COLUMN checkout_sessions.discount_total IS
    'Snapshot column. Combined promotion + coupon discount applied to this '
    'session''s total. CHECK (>= 0) -- a discount never goes negative '
    '(database-design §3.3 rule 3 wording, applied here the same way as '
    'orders.discount_total).';
COMMENT ON COLUMN checkout_sessions.shipping_fee IS
    'Snapshot column. Flat shipping fee composed by the pricing context '
    '(domain-model §1 pricing) at recalculation time.';
COMMENT ON COLUMN checkout_sessions.grand_total IS
    'Snapshot column. subtotal - discount_total + shipping_fee, as computed '
    'by pricing at recalculation time -- stored, not recomputed on read.';
COMMENT ON COLUMN checkout_sessions.expected_total IS
    'The total the client/cart believed BEFORE recalculation. Compared '
    'against grand_total to detect the mismatch that drives '
    'PENDING_CONFIRMATION vs. proceeding straight to reservation (ADR-0003 '
    'StartCheckoutUseCase, domain-model §1 checkout "customer''s '
    're-confirmation when the total changed", rule 2).';
COMMENT ON COLUMN checkout_sessions.currency IS
    'ISO 4217 alpha-3 currency code paired with every money column on this '
    'row (subtotal/discount_total/shipping_fee/grand_total/expected_total) -- '
    'one session, one currency. CHECK constraint enforces the fixed '
    '3-character length.';
COMMENT ON COLUMN checkout_sessions.payment_deadline IS
    'Payment-window deadline (rules 5, 8; the 15-minute window). NULL while '
    'PENDING_CONFIRMATION -- no window runs until stock is reserved and the '
    'session reaches AWAITING_PAYMENT. idx_checkout_sessions_deadline_open '
    'below is the sweep index ExpirePaymentWindowUseCase (ADR-0003) scans.';
COMMENT ON COLUMN checkout_sessions.idempotency_key IS
    'Client-generated idempotency key (api-guidelines §5): 1-255 chars, '
    'opaque to the server. Nullable at the schema level -- presence is '
    'enforced at the application layer for the specific mutations that '
    'require it (security-architecture §1.4 case 6), not by NOT NULL here. '
    'Uniqueness is scoped per customer, not global (api-guidelines §5.5 -- '
    '"keys are scoped per authenticated principal and per endpoint"). '
    'uq_checkout_sessions_idempotency below backs the per-customer '
    'replay-detection contract (§5 item 3: same key + same request -> '
    'original result).';
COMMENT ON COLUMN checkout_sessions.version IS
    'Optimistic-locking counter (database-design §7). Incremented by JPA on '
    'every UPDATE; a stale write raises OptimisticLockException -> 409. Not '
    'a business-rule column.';

-- Trigger: keep updated_at synchronised on every UPDATE row.
-- Reuses set_updated_at(), defined once in V001; not redefined here.
CREATE TRIGGER trg_checkout_sessions_updated_at
    BEFORE UPDATE ON checkout_sessions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Query served: payment-window expiry sweep (ADR-0003
-- ExpirePaymentWindowUseCase; rules 5, 8) -- "find all AWAITING_PAYMENT
-- sessions whose deadline has passed". Partial index limited to the
-- AWAITING_PAYMENT subset keeps it tiny; PENDING_CONFIRMATION (short-lived)
-- and EXPIRED (the majority over time) rows never appear in it. Name and
-- filter predicate exactly as specified in database-design §5.
CREATE INDEX idx_checkout_sessions_deadline_open
    ON checkout_sessions (payment_deadline)
    WHERE status = 'AWAITING_PAYMENT';

-- Query served: idempotent placement/payment replay detection
-- (api-guidelines §5, security-architecture §1.4 case 6) -- "has this
-- customer already used this idempotency key?" point lookup, and doubles as
-- the business rule enforcing it. Scoped per (customer_id, idempotency_key),
-- not globally, per api-guidelines §5.5 ("keys are scoped per authenticated
-- principal and per endpoint") -- a global unique constraint would let an
-- accidental or contrived key collision between two different customers
-- fail one customer's unrelated, legitimate checkout attempt with a
-- spurious 409. Partial on idempotency_key IS NOT NULL so sessions created
-- without a key (not every write path requires one, see column comment)
-- never occupy a slot in this index.
CREATE UNIQUE INDEX uq_checkout_sessions_idempotency
    ON checkout_sessions (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Query served: "does this customer have an in-flight checkout session?" /
-- "list this customer's checkout sessions" -- a customer-facing "resume
-- checkout" or support/admin lookup by customer. Not partial: unlike carts'
-- one-ACTIVE-per-identity rule, checkout sessions are not constrained to at
-- most one per customer at the DB level (a customer may have historical
-- EXPIRED sessions alongside a new PENDING_CONFIRMATION one), so a plain
-- b-tree on the FK column is the right shape rather than a partial unique.
CREATE INDEX idx_checkout_sessions_customer_id
    ON checkout_sessions (customer_id);

-- Query served: (a) the RESTRICT check when a delete is ever attempted
-- against carts.id; (b) "which checkout session, if any, is open for cart
-- X" -- looked up from the cart side when a customer resumes/re-enters
-- checkout for the same cart. Same rationale as idx_cart_items_product_id /
-- idx_stock_reservation_lines_product in prior migrations (FK-child index
-- backing a real query, not a blanket rule).
CREATE INDEX idx_checkout_sessions_cart_id
    ON checkout_sessions (cart_id);

-- No index added on reservation_id: the only query against it is the
-- RESTRICT check on stock_reservations.id deletes, and stock_reservations
-- rows are never deleted in practice (V004) -- same "no speculative index"
-- reasoning stock_items applied to itself in V004. A future admin
-- "which checkout session owns reservation X" screen would justify adding
-- one at that time, with its own query cited.

-- ---------------------------------------------------------------------------
-- 2. Least-privilege grants for ecommerce_app
--    Role already exists (created idempotently in V001); no CREATE ROLE
--    here. Schema-level USAGE ON SCHEMA public was already granted in V001
--    and is not scoped per-table, so it is not repeated. checkout_sessions
--    is a normal mutable table, not an audit/ledger/history table -- the
--    append-only grant pattern (§3.7) does not apply here.
-- ---------------------------------------------------------------------------

-- checkout_sessions: application performs SELECT / INSERT / UPDATE only.
-- DELETE is intentionally withheld -- a session's lifecycle is a status
-- change (PENDING_CONFIRMATION -> AWAITING_PAYMENT -> EXPIRED, more values
-- to follow), never a DELETE, same rule and DB-level backstop as
-- carts/stock_reservations.
GRANT SELECT, INSERT, UPDATE ON checkout_sessions TO ecommerce_app;

-- No new bigint identity sequence is created by this migration
-- (checkout_sessions.id is an application-supplied uuid, no
-- GENERATED ALWAYS AS IDENTITY column here) -- the blanket
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ecommerce_app from V001
-- already covers every sequence that exists at any point in time for
-- future-created tables in prior migrations; nothing to replicate here.

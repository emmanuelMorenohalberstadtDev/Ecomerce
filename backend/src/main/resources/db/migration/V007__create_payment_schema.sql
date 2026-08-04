-- =============================================================================
-- V007__create_payment_schema.sql
-- Context  : payment
-- Purpose  : Bootstrap the payment schema.
--            Creates: payments, payment_attempts, refunds.
--            Grants  : ecommerce_app role with least-privilege surface,
--                      including the append-only grant for payment_attempts
--                      and refunds (database-design §3.7).
--            Trigger : reuses set_updated_at() defined in V001 for
--                      payments.updated_at.
--
-- Does NOT touch: users, categories, products, product_images, carts,
-- cart_items (V001-V003), stock_items, stock_reservations,
-- stock_reservation_lines, stock_movements (V004), checkout_sessions (V005),
-- or orders, order_items, order_status_history (V006). payments.order_id
-- references orders(id) (V006) and payments.customer_id references
-- users(id) (V001) as cross-context FKs only (database-design §3.6) -- no
-- existing table is altered here.
--
-- Cross-context seam (ADR-0005): one Payment per order, aggregate root of
-- this context. Payment initiates a charge synchronously against order's
-- new OrderPaymentPort facade on gateway APPROVED only -- that call, and the
-- resulting Order.markPaid, happen inside the SAME physical DB transaction
-- as the INSERT/UPDATE this migration's tables perform (ADR-0001 "one
-- deployment, one DB" premise). Declines/timeouts never touch any order-side
-- table or port; they are recorded on payment_attempts only and the
-- aggregate stays PENDING so the customer can retry within the payment
-- window (see payments.status column comment below -- this is the load-
-- bearing schema decision this migration encodes for ADR-0005). Refund is a
-- separate, later, asynchronous flow: payment.application.listener
-- subscribes to order.domain.event.OrderCancelledEvent via
-- @TransactionalEventListener(AFTER_COMMIT) and inserts a refunds row --
-- order's own transaction has already committed by then, no atomicity
-- requirement ties refunds back to the order row (ADR-0005 item 4).
--
-- Safety analysis
-- ---------------
-- Lock profile  : Three brand-new, empty tables (payments, payment_attempts,
--                 refunds). ACCESS EXCLUSIVE locks are taken only on these
--                 new relations as they are created. The FKs from
--                 payments.order_id -> orders(id) and payments.customer_id
--                 -> users(id) to existing, already-populated tables take
--                 only a brief ROW SHARE lock on those tables to validate
--                 the constraint -- it does not block reads/writes on them,
--                 and there is no pre-existing data in any table created
--                 here to validate against (all three are created empty in
--                 this same migration), so FK validation is effectively
--                 instant. No CONCURRENTLY needed for any index below --
--                 every index here is built on a table containing zero rows
--                 at migration time.
-- Destructive?  : No. Greenfield creation only; no ALTER/DROP of existing
--                 objects.
-- Backfill?     : No data exists in these tables yet.
-- Forward-fix   : N/A -- no previous version of these tables to break.
--                 Purely additive from the V001-V006 deployed app's point of
--                 view.
-- Rollback      : DROP TABLE refunds, payment_attempts, then payments
--                 (reverse FK dependency order). Dev only; production
--                 follows expand-migrate-contract -- a new forward version,
--                 never an edit of this file.
-- Testcontainer : Reviewed statically against V006's conventions (constraint
--                 naming prefixes pk_/fk_/uq_/ck_/idx_, GUC/grant shape,
--                 header structure). NOT executed against a real/
--                 Testcontainers Postgres in this environment -- no
--                 container runtime was available to run the two-path
--                 (clean V001->V007; V006-state->V007) verification the
--                 flyway skill mandates. Flagged for test-engineer to run
--                 that verification before this migration is considered
--                 done, per the flyway skill's mandatory rule.
-- Applied       : 2026-08-02  (append-only -- never modify after first apply)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Table: payments
--    Aggregate root of the payment bounded context (database-design §1;
--    domain-model §2/§3 `Payment`; ADR-0005). One row per order -- order_id
--    is NOT NULL + UNIQUE, not a one-to-many. ID is UUIDv7, generated
--    application-side (ADR-0002 + security §6a, database-design §4), same
--    convention as orders.id/carts.id/stock_reservations.id/
--    checkout_sessions.id: no DEFAULT on id -- a missing id fails loudly
--    instead of silently falling back to a DB-generated value.
--
--    status decision (ADR-0005, this migration's judgment call): 3 values
--    only -- PENDING (default) / CAPTURED / REFUNDED. There is no DECLINED
--    or FAILED value here. A declined or timed-out PaymentAttempt does NOT
--    move this aggregate to any terminal status -- it stays PENDING so
--    SubmitPaymentUseCase can be retried within the checkout payment window
--    (ADR-0005 item 2/3: payment never force-fails an order, and by the same
--    reasoning never force-fails itself into a dead-end status; the outcome
--    of each attempt lives on payment_attempts.outcome instead, see §2
--    below). status only ever moves PENDING -> CAPTURED (on a gateway
--    APPROVED outcome, in the same transaction as Order.markPaid) and
--    CAPTURED -> REFUNDED (on OrderCancelledEvent, async, ADR-0005 item 4).
--    This CHECK only guards the set of legal values -- the transition graph
--    itself is a domain concern (Payment.markCaptured/markRefunded), not
--    enforced here, same split V006 already established for orders.status.
--
--    customer_id is denormalized onto this row (not reached only via a join
--    through orders) so ownership-scoped reads (e.g. "is this payment even
--    this customer's, before doing anything with it") do not need a join
--    back through orders -- same reasoning checkout_sessions.customer_id
--    (V005) and orders.customer_id (V006) already use.
--
--    Money columns follow this schema's established numeric(19,4) + char(3)
--    convention (database-design §4), NOT bigint cents -- exactly matching
--    orders.grand_total/orders.currency (V006). amount is the authoritative
--    charge amount for this payment, sourced exclusively from
--    OrderPaymentPort.getForPayment(...).grandTotal() (ADR-0005 sanity
--    check: "never a client-supplied amount") -- never re-derived from a
--    join once captured.
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id            uuid           NOT NULL,

    -- Cross-context, by-ID reference to order's aggregate root (V006). NOT
    -- NULL + UNIQUE: exactly one Payment per order (ADR-0005 -- "one
    -- Payment per order, accumulating multiple PaymentAttempt children
    -- across retries"). RESTRICT, matching every other cross-context FK in
    -- this schema -- orders are never deleted (V006 grants: no DELETE).
    order_id      uuid           NOT NULL,

    -- Denormalized for ownership-scoped reads without a join through orders
    -- -- see table comment above. RESTRICT, matching orders.customer_id
    -- (V006) and every other users(id) FK in this schema -- a customer with
    -- payment history cannot be deleted.
    customer_id   uuid           NOT NULL,

    -- PENDING (default) / CAPTURED / REFUNDED -- 3 values only. See table
    -- comment above for the full ADR-0005 reasoning (no DECLINED/FAILED
    -- value at this level -- that lives on payment_attempts.outcome).
    status        text           NOT NULL DEFAULT 'PENDING',

    -- Authoritative charge amount, snapshot from order's grand_total at
    -- attempt time (database-design §4 money convention: numeric(19,4) +
    -- char(3), always paired). Never a client-supplied value (ADR-0005
    -- sanity check).
    amount        numeric(19,4)  NOT NULL,
    currency      char(3)        NOT NULL,

    -- Optimistic-locking column (database-design §4/§7). Maps to @Version
    -- in JPA; concurrent updates (e.g. a retried attempt racing a refund)
    -- raise OptimisticLockException -> 409. Same convention as orders
    -- .version / carts.version / stock_items.version / checkout_sessions
    -- .version.
    version       bigint         NOT NULL DEFAULT 0,

    created_at    timestamptz    NOT NULL DEFAULT now(),

    -- Bumped by trg_payments_updated_at on every subsequent status
    -- transition (CAPTURED/REFUNDED). Blanket convention (database-design
    -- §4): every table gets one.
    updated_at    timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT pk_payments PRIMARY KEY (id),

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,

    CONSTRAINT fk_payments_customer
        FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- "One Payment per order" (ADR-0005) -- declared inline, matching
    -- uq_orders_reservation's shape in V006. Also serves as the index the
    -- RESTRICT-check on orders.id deletes and any "does this order already
    -- have a payment" lookup would use.
    CONSTRAINT uq_payments_order UNIQUE (order_id),

    CONSTRAINT ck_payments_status
        CHECK (status IN ('PENDING', 'CAPTURED', 'REFUNDED')),

    CONSTRAINT ck_payments_amount
        CHECK (amount >= 0),
    CONSTRAINT ck_payments_currency
        CHECK (char_length(currency) = 3),

    CONSTRAINT ck_payments_version
        CHECK (version >= 0)
);

COMMENT ON TABLE  payments IS
    'Aggregate root of the payment context: one row per order, accumulating '
    'multiple PaymentAttempt children across retries (ADR-0005; '
    'domain-model §2/§3 Payment). Never deleted -- lifecycle is a status '
    'change only, enforced as the DB-level backstop by withholding DELETE '
    'at the grant level (§4 below), matching orders/carts/'
    'stock_reservations/checkout_sessions. order_id is NOT NULL and UNIQUE '
    '-- see column comment for the full reasoning.';
COMMENT ON COLUMN payments.id IS
    'UUIDv7 generated by the application before INSERT (ADR-0002). '
    'No DB default -- omitting id in an INSERT is a programmer error, not a '
    'silent fallback.';
COMMENT ON COLUMN payments.order_id IS
    'By-ID reference to order''s orders.id (cross-context FK, database-'
    'design §3.6). NOT NULL + UNIQUE: exactly one Payment per order '
    '(ADR-0005). ON DELETE RESTRICT: orders are never deleted (V006), this '
    'is the DB-level backstop anyway, matching every other cross-context FK '
    'in this schema.';
COMMENT ON COLUMN payments.customer_id IS
    'Owning registered customer, denormalized off orders.customer_id so '
    'ownership-scoped reads do not need a join back through orders -- same '
    'reasoning checkout_sessions.customer_id (V005) and orders.customer_id '
    '(V006) already use. ON DELETE RESTRICT: a customer with payment '
    'history cannot be deleted.';
COMMENT ON COLUMN payments.status IS
    'PENDING (default) / CAPTURED / REFUNDED -- 3 values only (ADR-0005). '
    'A declined or timed-out PaymentAttempt never moves this column -- it '
    'stays PENDING so the customer can retry within the checkout payment '
    'window; the per-attempt outcome lives on payment_attempts.outcome '
    'instead. This CHECK only guards the set of legal values -- the '
    'transition graph (PENDING -> CAPTURED -> REFUNDED, one-directional) is '
    'a domain concern (Payment.markCaptured/markRefunded), not enforced '
    'here.';
COMMENT ON COLUMN payments.amount IS
    'Authoritative charge amount, sourced exclusively from '
    'OrderPaymentPort.getForPayment(...).grandTotal() (ADR-0005) -- never a '
    'client-supplied value, never re-derived via a join once captured.';
COMMENT ON COLUMN payments.currency IS
    'ISO 4217 alpha-3 currency code paired with amount (database-design '
    '§4). CHECK constraint enforces the fixed 3-character length.';
COMMENT ON COLUMN payments.version IS
    'Optimistic-locking counter (database-design §7), mapped as JPA '
    '@Version. A stale concurrent UPDATE (e.g. capture racing a refund) '
    'raises OptimisticLockException -> 409.';
COMMENT ON COLUMN payments.updated_at IS
    'Bumped by trg_payments_updated_at on every subsequent status-'
    'transition UPDATE. Blanket convention (database-design §4), same as '
    'every other table in this schema.';

-- Trigger: keep updated_at synchronised on every UPDATE row.
-- Reuses set_updated_at(), defined once in V001; not redefined here.
CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- No separate index needed for the RESTRICT-check / "does this order
-- already have a payment" lookup on order_id -- the uq_payments_order
-- UNIQUE constraint declared inline in the CREATE TABLE above already backs
-- both (Postgres creates a unique b-tree index for it automatically).
--
-- No customer_id index added here: payments is 1:1 with orders (not
-- 1:many), so a "list this customer's payments" query is redundant with
-- order history (already served by idx_orders_customer_placed, V006) --
-- adding one now would be speculative (skill `indexing` rule: every index
-- names its query). The ownership-scoped point read this table is designed
-- for (WHERE order_id = :orderId AND customer_id = :customerId) is already
-- narrowed to at most one row by the order_id predicate via
-- uq_payments_order -- customer_id does not need to appear in an index for
-- that query to be fast.

-- ---------------------------------------------------------------------------
-- 2. Table: payment_attempts
--    Child rows of payments (one-to-many, ON DELETE CASCADE) -- owned
--    entities within the Payment aggregate (domain-model §2/§3
--    `PaymentAttempt`), append-mostly: one row per charge attempt, exactly
--    like order_status_history under orders (V006) and stock_movements
--    under stock_items (V004). PK is bigint identity -- internal child row,
--    never client-addressed as an aggregate root (ADR-0002 ID conventions).
--
--    idempotency_key + uq_payment_attempts_idempotency give true idempotent
--    replay: the client-supplied `Idempotency-Key` header value, scoped per
--    (payment_id, idempotency_key) -- same key presented again against the
--    same payment returns the original outcome without a second gateway
--    call (SubmitPaymentUseCase looks this up before charging). Scoped per
--    payment, not global, same reasoning uq_checkout_sessions_idempotency
--    (V005) already applied per-customer rather than globally -- an
--    accidental or contrived key collision between two different payments
--    must not corrupt one payment's unrelated attempt.
--
--    NO PAN or CVV column exists anywhere on this table, deliberately
--    (security-architecture §1.3: "No PAN ever enters the system"). Only
--    gateway_reference -- the simulated/real PSP's opaque reference -- is
--    stored. This is a security invariant enforced by omission: do not add
--    a card-shaped column to this table without a superseding ADR and
--    security-engineer sign-off.
-- ---------------------------------------------------------------------------
CREATE TABLE payment_attempts (
    id                 bigint       GENERATED ALWAYS AS IDENTITY,

    -- Parent -> child inside one aggregate (database-design §3.6) -- CASCADE.
    payment_id         uuid         NOT NULL,

    -- Client-supplied `Idempotency-Key` header value (api-guidelines §5).
    -- NOT NULL here -- unlike checkout_sessions.idempotency_key (V005,
    -- nullable because that table also backs non-idempotency-guarded
    -- writes), every row in this table is itself the record of one
    -- idempotency-guarded charge attempt, so a key is always present.
    idempotency_key    varchar(128) NOT NULL,

    -- APPROVED / DECLINED / TIMEOUT (ADR-0005 item 2; PaymentGatewayPort
    -- outcome). text + CHECK, matching every other status/outcome column
    -- convention in this schema.
    outcome            text         NOT NULL,

    -- Populated only when outcome = 'DECLINED' -- see
    -- ck_payment_attempts_decline_reason_requires_declined below, mirroring
    -- how order_status_history.from_status (V006) is nullable only for one
    -- specific row-type.
    decline_reason     text,

    -- Simulated/real PSP's opaque reference. NEVER any card-shaped data --
    -- see table comment above.
    gateway_reference  varchar(128),

    -- Snapshot of what was attempted (database-design §4 money convention),
    -- copied from payments.amount/currency at attempt time -- not re-read
    -- from the parent row after the fact, so a historical attempt row
    -- always shows exactly what was charged when it ran even if a future
    -- schema change ever allowed payments.amount to move.
    amount             numeric(19,4) NOT NULL,
    currency           char(3)      NOT NULL,

    attempted_at       timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_attempts PRIMARY KEY (id),

    CONSTRAINT fk_payment_attempts_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,

    CONSTRAINT ck_payment_attempts_outcome
        CHECK (outcome IN ('APPROVED', 'DECLINED', 'TIMEOUT')),

    CONSTRAINT ck_payment_attempts_decline_reason
        CHECK (decline_reason IS NULL OR decline_reason IN
            ('INSUFFICIENT_FUNDS', 'CARD_DECLINED', 'FRAUD_SUSPECTED',
             'GATEWAY_TIMEOUT', 'GENERIC_DECLINE')),

    -- Cross-column guard: decline_reason is only ever populated alongside
    -- outcome = 'DECLINED' -- an APPROVED or TIMEOUT row can never carry a
    -- decline reason. Same cross-column CHECK discipline V006 already
    -- applied via ck_order_status_history_transition_changes_status.
    CONSTRAINT ck_payment_attempts_decline_reason_requires_declined
        CHECK (outcome = 'DECLINED' OR decline_reason IS NULL),

    CONSTRAINT ck_payment_attempts_amount
        CHECK (amount >= 0),
    CONSTRAINT ck_payment_attempts_currency
        CHECK (char_length(currency) = 3),

    -- True idempotent replay, scoped per payment -- see table comment above.
    CONSTRAINT uq_payment_attempts_idempotency
        UNIQUE (payment_id, idempotency_key)
);

COMMENT ON TABLE  payment_attempts IS
    'Append-mostly: one row per charge attempt against a Payment (ADR-0005; '
    'domain-model §2/§3 PaymentAttempt). ecommerce_app role has INSERT + '
    'SELECT only -- no UPDATE or DELETE (same enforcement pattern as '
    'order_status_history V006 / stock_movements V004). NO PAN/CVV column '
    'exists anywhere on this table, deliberately (security-architecture '
    '§1.3 "No PAN ever enters the system") -- do not add one without a '
    'superseding ADR.';
COMMENT ON COLUMN payment_attempts.payment_id IS
    'By-ID reference to the parent payment. ON DELETE CASCADE -- child row '
    'inside the Payment aggregate (database-design §3.6); in practice '
    'payments are never deleted, so this CASCADE exists for schema '
    'correctness/dev-cleanup only, same note V006 gave order_items/'
    'order_status_history.';
COMMENT ON COLUMN payment_attempts.idempotency_key IS
    'Client-supplied Idempotency-Key header value (api-guidelines §5). '
    'NOT NULL -- every row here is itself one idempotency-guarded charge '
    'attempt. uq_payment_attempts_idempotency below backs true idempotent '
    'replay: the same key presented again against the same payment returns '
    'this row''s original outcome instead of invoking the gateway a second '
    'time -- SubmitPaymentUseCase looks this up before charging.';
COMMENT ON COLUMN payment_attempts.outcome IS
    'APPROVED / DECLINED / TIMEOUT -- the PaymentGatewayPort result for '
    'this attempt (ADR-0005 item 2). Payment.markCaptured is only ever '
    'called after an APPROVED outcome; DECLINED/TIMEOUT leave the parent '
    'payments.status untouched at PENDING so the attempt is retryable.';
COMMENT ON COLUMN payment_attempts.decline_reason IS
    'INSUFFICIENT_FUNDS / CARD_DECLINED / FRAUD_SUSPECTED / '
    'GATEWAY_TIMEOUT / GENERIC_DECLINE, or NULL. Populated only when '
    'outcome = ''DECLINED'' -- '
    'ck_payment_attempts_decline_reason_requires_declined enforces the '
    'pairing at the database level.';
COMMENT ON COLUMN payment_attempts.gateway_reference IS
    'Simulated/real PSP''s opaque reference for this attempt. Never any '
    'card-shaped data (no PAN/CVV column exists on this table -- see table '
    'comment). Nullable: a TIMEOUT attempt may have no reference to record.';
COMMENT ON COLUMN payment_attempts.amount IS
    'Snapshot of the amount attempted, copied from the parent payment at '
    'attempt time (database-design §4 money convention) -- a historical row '
    'always reflects exactly what was charged when this attempt ran.';
COMMENT ON COLUMN payment_attempts.currency IS
    'Snapshot currency code, paired with amount (database-design §4).';
COMMENT ON COLUMN payment_attempts.attempted_at IS
    'When this attempt ran. No separate index for "load all attempts for '
    'this payment" -- uq_payment_attempts_idempotency''s leading column '
    '(payment_id) already backs that scan and the CASCADE-delete lookup, '
    'same judgment call V006 made for order_items relying on '
    'uq_order_items_order_product''s leading column.';

-- No separate idx_payment_attempts_payment_id -- uq_payment_attempts
-- _idempotency (payment_id, idempotency_key) above already provides a
-- leading-column index on payment_id, covering both the CASCADE-delete
-- lookup and "load all attempts for this payment" (payment detail view).
-- Adding a second, narrower index on payment_id alone would be a redundant
-- prefix of this one (skill `indexing` rule against speculative/redundant
-- indexes) -- same judgment call V006 made for orders.reservation_id
-- relying solely on uq_orders_reservation.

-- ---------------------------------------------------------------------------
-- 3. Table: refunds
--    Child rows of payments (one-to-many, ON DELETE CASCADE) -- owned
--    entities within the Payment aggregate (domain-model §2/§3 `Refund`),
--    append-only: one row per refund issued. PK is bigint identity --
--    internal child row, never client-addressed as an aggregate root
--    (ADR-0002 ID conventions). v1 has exactly one trigger:
--    OrderCancelledEvent on a captured payment (ADR-0005 item 4,
--    domain-model §1 payment "Consumes OrderCancelledEvent to issue
--    refunds, rule 7"), issued asynchronously via
--    @TransactionalEventListener(AFTER_COMMIT) -- order's own transaction
--    has already committed by the time this row is inserted, so there is no
--    atomicity requirement tying this table back to the orders row (unlike
--    payments/payment_attempts, which share a transaction with order's
--    markPaid on the charge path).
-- ---------------------------------------------------------------------------
CREATE TABLE refunds (
    id                 bigint        GENERATED ALWAYS AS IDENTITY,

    -- Parent -> child inside one aggregate (database-design §3.6) -- CASCADE.
    payment_id         uuid          NOT NULL,

    -- Snapshot of the amount refunded (database-design §4 money
    -- convention). v1 always refunds the full captured amount (no partial
    -- refund use case) -- stored as its own column anyway rather than
    -- implicitly read from the parent payment, matching this schema's
    -- snapshot-column convention (rule 3) for every other money-adjacent
    -- child table.
    amount             numeric(19,4) NOT NULL,
    currency           char(3)       NOT NULL,

    -- ORDER_CANCELLED -- exactly one reason in v1 (ADR-0005 item 4 is the
    -- only trigger this migration supports). Widening this set later (e.g.
    -- an admin-initiated refund) is a trivial expand-migration
    -- (ALTER ... ADD ... CHECK ... NOT VALID + VALIDATE, per the flyway
    -- skill's additive-CHECK example) -- not built now (YAGNI, no stated
    -- v1 requirement beyond cancellation).
    reason             text          NOT NULL,

    -- Simulated/real PSP's opaque reference for the refund transaction.
    -- Never any card-shaped data, same rule as payment_attempts
    -- .gateway_reference.
    gateway_reference  varchar(128),

    issued_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_refunds PRIMARY KEY (id),

    CONSTRAINT fk_refunds_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,

    CONSTRAINT ck_refunds_amount
        CHECK (amount >= 0),
    CONSTRAINT ck_refunds_currency
        CHECK (char_length(currency) = 3),

    CONSTRAINT ck_refunds_reason
        CHECK (reason IN ('ORDER_CANCELLED'))
);

COMMENT ON TABLE  refunds IS
    'Append-only: one row per refund issued on a captured payment '
    '(ADR-0005 item 4; domain-model §1/§2 Refund, rule 7). ecommerce_app '
    'role has INSERT + SELECT only -- no UPDATE or DELETE (same '
    'enforcement pattern as payment_attempts above / order_status_history '
    'V006 / stock_movements V004). v1''s sole trigger is '
    'OrderCancelledEvent on a captured payment, consumed asynchronously via '
    '@TransactionalEventListener(AFTER_COMMIT) -- no atomicity requirement '
    'ties this table back to the orders row (order''s own transaction has '
    'already committed by the time a refund row is inserted).';
COMMENT ON COLUMN refunds.payment_id IS
    'By-ID reference to the parent payment. ON DELETE CASCADE -- child row '
    'inside the Payment aggregate (database-design §3.6); in practice '
    'payments are never deleted, so this CASCADE exists for schema '
    'correctness/dev-cleanup only, same note given to payment_attempts '
    '.payment_id above.';
COMMENT ON COLUMN refunds.amount IS
    'Snapshot of the amount refunded (database-design §4 money '
    'convention). v1 always refunds the full captured amount -- no partial-'
    'refund use case exists yet.';
COMMENT ON COLUMN refunds.currency IS
    'Snapshot currency code, paired with amount (database-design §4).';
COMMENT ON COLUMN refunds.reason IS
    'ORDER_CANCELLED -- the only reason v1 supports (ADR-0005 item 4). '
    'Widening this set is a trivial additive-CHECK expand-migration when a '
    'second trigger (e.g. admin-initiated refund) becomes a real '
    'requirement -- not built speculatively now.';
COMMENT ON COLUMN refunds.gateway_reference IS
    'Simulated/real PSP''s opaque reference for the refund transaction. '
    'Never any card-shaped data, same rule as payment_attempts '
    '.gateway_reference -- no PAN/CVV column exists on this table.';
COMMENT ON COLUMN refunds.issued_at IS
    'When this refund was issued. idx_refunds_payment below is the index a '
    'payment-detail "refund history" view scans (database-design §5).';

-- Query served: payment detail view -- "load all refunds for this payment,
-- in order" (same shape as idx_order_status_history_order, V006). Also
-- covers the CASCADE-delete lookup. Unlike payment_attempts, refunds has no
-- UNIQUE constraint whose leading column is payment_id, so this index is
-- added explicitly rather than relying on one.
CREATE INDEX idx_refunds_payment
    ON refunds (payment_id, issued_at);

-- ---------------------------------------------------------------------------
-- 4. Least-privilege grants for ecommerce_app
--    Role already exists (created idempotently in V001); no CREATE ROLE
--    here. Schema-level USAGE ON SCHEMA public was already granted in V001
--    and is not scoped per-table, so it is not repeated.
-- ---------------------------------------------------------------------------

-- payments: application performs SELECT / INSERT / UPDATE only. DELETE is
-- intentionally withheld -- a payment's lifecycle is a status change, never
-- a DELETE, same rule and DB-level backstop as orders/carts/
-- stock_reservations/checkout_sessions.
GRANT SELECT, INSERT, UPDATE ON payments TO ecommerce_app;

-- payment_attempts: INSERT and SELECT ONLY. UPDATE and DELETE are
-- permanently denied at the DB level (database-design §3.7). This is the
-- second enforcement layer; the application layer
-- (PaymentAttemptRepository) enforces the same contract in code --
-- identical pattern to admin_audit_log (V001), stock_movements (V004), and
-- order_status_history (V006).
GRANT SELECT, INSERT ON payment_attempts TO ecommerce_app;

-- refunds: INSERT and SELECT ONLY, same reasoning and enforcement pattern
-- as payment_attempts above -- a refund, once issued, is never edited or
-- removed.
GRANT SELECT, INSERT ON refunds TO ecommerce_app;

-- bigint identity sequences created in this migration (payment_attempts.id,
-- refunds.id). V001's blanket
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA public only covered sequences that
-- existed at the time V001 ran; it does not retroactively cover sequences
-- created by later migrations, so it is replicated here (same pattern
-- V002/V003/V004/V006 followed).
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ecommerce_app;

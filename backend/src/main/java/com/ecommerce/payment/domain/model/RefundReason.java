package com.ecommerce.payment.domain.model;

/**
 * Why a refund was issued, matching {@code ck_refunds_reason} in
 * {@code V007__create_payment_schema.sql}. Exactly one value in v1 —
 * {@code ORDER_CANCELLED}, ADR-0005 item 4's sole trigger (a captured payment refunded when its
 * order is cancelled). Widening this set (e.g. an admin-initiated refund) is a trivial additive
 * expand-migration when that becomes a real requirement — not built speculatively now (YAGNI, per
 * the migration's own comment).
 */
public enum RefundReason {
    ORDER_CANCELLED
}

package com.ecommerce.checkout.domain.model;

/**
 * Lifecycle status of a {@link CheckoutSession} (domain-model.md §2; {@code V005__create_checkout_schema.sql}
 * {@code ck_checkout_sessions_status}).
 *
 * <p>One-way transition graph: {@code PENDING_CONFIRMATION -> AWAITING_PAYMENT -> EXPIRED}. Never
 * any other edge. {@code PAID}/{@code CANCELLED} are deliberately not modelled yet — out of scope
 * for this change (ADR-0003; the migration's column comment says the same), arriving with the
 * payment/order hand-off work as a new migration extending the CHECK constraint.
 */
public enum SessionStatus {

    /** Recalculated total does not yet match {@code expectedTotal}, or no reservation exists yet. */
    PENDING_CONFIRMATION,

    /** Totals confirmed, stock reserved, payment window running. */
    AWAITING_PAYMENT,

    /** Payment window lapsed; the sweep released the reservation. */
    EXPIRED
}

package com.ecommerce.payment.domain.model;

/**
 * The payment lifecycle status (domain-model.md §1/§2 {@code Payment}; ADR-0005).
 *
 * <p>Exactly 3 values, matching {@code ck_payments_status} in
 * {@code V007__create_payment_schema.sql}: {@code PENDING} (default, retryable) ->
 * {@code CAPTURED} (a gateway {@code APPROVED} outcome, in the same transaction as
 * {@code Order.markPaid}) -> {@code REFUNDED} (on {@code OrderCancelledEvent}, async, ADR-0005
 * item 4). One-directional, never backward.
 *
 * <p><strong>Deliberately no {@code DECLINED}/{@code FAILED} value here</strong> — a declined or
 * timed-out {@link PaymentAttempt} never moves this aggregate's status; it stays {@code PENDING}
 * so {@code SubmitPaymentUseCase} can be retried within the checkout payment window (ADR-0005 item
 * 2/3: payment never force-fails an order, and by the same reasoning never force-fails itself into
 * a dead-end status). The per-attempt outcome lives on {@link PaymentAttempt#outcome()} instead.
 *
 * <p>The legal-transition set is enforced by {@link Payment}'s named domain methods
 * ({@code markCaptured}/{@code markRefunded}), never by this enum itself — this type only names
 * the three legal values, matching {@code ck_payments_status}'s own comment: "This CHECK only
 * guards the set of legal values -- the transition graph itself is a domain concern."
 */
public enum PaymentStatus {
    PENDING,
    CAPTURED,
    REFUNDED
}

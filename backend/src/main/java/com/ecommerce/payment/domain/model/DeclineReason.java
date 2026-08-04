package com.ecommerce.payment.domain.model;

/**
 * Why a charge attempt was declined, matching {@code ck_payment_attempts_decline_reason} in
 * {@code V007__create_payment_schema.sql}. Populated only when {@link PaymentAttempt#outcome()} is
 * {@link PaymentOutcome#DECLINED} — enforced in-memory by {@link PaymentAttempt}'s compact
 * constructor, mirroring the DB's own
 * {@code ck_payment_attempts_decline_reason_requires_declined} cross-column guard.
 */
public enum DeclineReason {
    INSUFFICIENT_FUNDS,
    CARD_DECLINED,
    FRAUD_SUSPECTED,
    GATEWAY_TIMEOUT,
    GENERIC_DECLINE
}

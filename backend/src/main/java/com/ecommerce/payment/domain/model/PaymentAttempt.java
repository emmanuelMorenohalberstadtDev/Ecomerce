package com.ecommerce.payment.domain.model;

import com.ecommerce.shared.money.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * Internal entity of the {@link Payment} aggregate — one charge attempt (domain-model.md §2/§3
 * {@code PaymentAttempt}; maps to {@code payment_attempts}, {@code V007__create_payment_schema.sql}).
 *
 * <p>Immutable value-style record, consistent with {@code inventory.domain.model.ReservedLine} and
 * {@code order.domain.model.OrderStatusTransition}: written once, when the attempt is made, and
 * never mutated afterward — mirrors {@code payment_attempts}' insert-only grants
 * ({@code SELECT, INSERT} only, V007 §4).
 *
 * <p>{@code amount} is a snapshot of what was attempted, copied from the parent {@link Payment} at
 * attempt time (V007's column comment: "a historical row always reflects exactly what was charged
 * when this attempt ran") — never re-derived from the parent after the fact.
 *
 * <p><strong>Cross-field invariant enforced here, mirroring the DB's own
 * {@code ck_payment_attempts_decline_reason_requires_declined}</strong>: {@code declineReason} is
 * non-null if and only if {@code outcome == DECLINED}.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record PaymentAttempt(String idempotencyKey, PaymentOutcome outcome, DeclineReason declineReason,
                              String gatewayReference, Money amount, Instant attemptedAt) {

    public PaymentAttempt {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("PaymentAttempt idempotencyKey must not be null or blank");
        }
        Objects.requireNonNull(outcome, "PaymentAttempt outcome must not be null");
        if (outcome == PaymentOutcome.DECLINED && declineReason == null) {
            throw new IllegalArgumentException("PaymentAttempt declineReason is required when outcome is DECLINED");
        }
        if (outcome != PaymentOutcome.DECLINED && declineReason != null) {
            throw new IllegalArgumentException("PaymentAttempt declineReason must be null unless outcome is DECLINED");
        }
        Objects.requireNonNull(amount, "PaymentAttempt amount must not be null");
        Objects.requireNonNull(attemptedAt, "PaymentAttempt attemptedAt must not be null");
    }
}

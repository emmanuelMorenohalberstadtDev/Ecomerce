package com.ecommerce.payment.domain.model;

import com.ecommerce.shared.money.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * Internal entity of the {@link Payment} aggregate — one refund issued on a captured payment
 * (domain-model.md §2/§3 {@code Refund}; maps to {@code refunds},
 * {@code V007__create_payment_schema.sql}).
 *
 * <p>Immutable value-style record, append-only — mirrors {@code refunds}' insert-only grants
 * ({@code SELECT, INSERT} only, V007 §4). v1 always refunds the full captured amount (no
 * partial-refund use case); {@code amount} is still its own snapshot column rather than implicitly
 * read from the parent, matching this schema's snapshot-column convention for every other
 * money-adjacent child table.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record Refund(Money amount, RefundReason reason, String gatewayReference, Instant issuedAt) {

    public Refund {
        Objects.requireNonNull(amount, "Refund amount must not be null");
        Objects.requireNonNull(reason, "Refund reason must not be null");
        Objects.requireNonNull(issuedAt, "Refund issuedAt must not be null");
    }
}

package com.ecommerce.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping one row of the append-only {@code payment_attempts} table
 * ({@code V007__create_payment_schema.sql}).
 *
 * <p>{@code id} is {@code GenerationType.IDENTITY} (bigint identity) — an internal child row, never
 * client-addressed. {@code paymentId} is a plain column, not a {@code @ManyToOne}/
 * {@code @JoinColumn} relationship — see {@code PaymentJpaEntity}'s class javadoc. Rows are
 * inserted once and never updated or deleted ({@code payment_attempts} grants:
 * {@code SELECT, INSERT} only) — this entity has no update path at all, mirroring
 * {@code order.infrastructure.persistence.OrderStatusHistoryJpaEntity}.
 *
 * <p>{@code declineReason} is nullable — populated only when {@code outcome = DECLINED}, matching
 * the column exactly.
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String outcome;

    @Column(name = "decline_reason", updatable = false)
    private String declineReason;

    @Column(name = "gateway_reference", updatable = false)
    private String gatewayReference;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "attempted_at", updatable = false)
    private Instant attemptedAt;

    PaymentAttemptJpaEntity() {}

    public PaymentAttemptJpaEntity(UUID paymentId, String idempotencyKey, String outcome, String declineReason,
                                   String gatewayReference, BigDecimal amount, String currency,
                                   Instant attemptedAt) {
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.outcome = outcome;
        this.declineReason = declineReason;
        this.gatewayReference = gatewayReference;
        this.amount = amount;
        this.currency = currency;
        this.attemptedAt = attemptedAt;
    }

    public Long getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOutcome() { return outcome; }
    public String getDeclineReason() { return declineReason; }
    public String getGatewayReference() { return gatewayReference; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getAttemptedAt() { return attemptedAt; }
}

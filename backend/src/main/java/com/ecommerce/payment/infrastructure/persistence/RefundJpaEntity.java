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
 * JPA entity mapping one row of the append-only {@code refunds} table
 * ({@code V007__create_payment_schema.sql}).
 *
 * <p>{@code id} is {@code GenerationType.IDENTITY} (bigint identity) — an internal child row, never
 * client-addressed. {@code paymentId} is a plain column, not a {@code @ManyToOne}/
 * {@code @JoinColumn} relationship — see {@code PaymentJpaEntity}'s class javadoc. Rows are
 * inserted once and never updated or deleted ({@code refunds} grants: {@code SELECT, INSERT}
 * only).
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "refunds")
public class RefundJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "gateway_reference", updatable = false)
    private String gatewayReference;

    @Column(name = "issued_at", updatable = false)
    private Instant issuedAt;

    RefundJpaEntity() {}

    public RefundJpaEntity(UUID paymentId, BigDecimal amount, String currency, String reason,
                           String gatewayReference, Instant issuedAt) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.gatewayReference = gatewayReference;
        this.issuedAt = issuedAt;
    }

    public Long getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReason() { return reason; }
    public String getGatewayReference() { return gatewayReference; }
    public Instant getIssuedAt() { return issuedAt; }
}

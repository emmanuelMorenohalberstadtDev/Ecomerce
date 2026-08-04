package com.ecommerce.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code payments} header table ({@code V007__create_payment_schema.sql}).
 *
 * <p>Design notes, mirroring {@code order.infrastructure.persistence.OrderJpaEntity} exactly:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7 before INSERT
 *       (ADR-0002).</li>
 *   <li><strong>No JPA relationship to {@code payment_attempts}/{@code refunds}:</strong>
 *       deliberately NOT mapped as {@code @OneToMany} collections — both child tables are
 *       write-once-per-row/append-only, and neither {@code PaymentAttempt} nor {@code Refund}
 *       carries the DB-generated {@code bigint identity} id back into the domain layer, so
 *       Hibernate's {@code merge()} would re-INSERT every rebuilt child on every header save. See
 *       {@code OrderJpaEntity}'s class javadoc for the full reasoning — same hazard, same fix:
 *       {@link JpaPaymentRepository} persists each child individually via its own targeted DAO.</li>
 *   <li>{@code @Version} maps {@code payments.version} for optimistic locking (database-design.md
 *       §7).</li>
 *   <li><strong>{@code implements Persistable<UUID>}, {@code isNew()} always {@code false}:</strong>
 *       same reasoning as {@code OrderJpaEntity}/{@code CheckoutSessionJpaEntity} — a
 *       manually-assigned id plus {@code @Version} together would otherwise make Spring Data JPA
 *       misclassify a first-time update (version still {@code 0}) as an INSERT attempt.</li>
 *   <li>{@code updated_at} is trigger-managed at the DB level ({@code trg_payments_updated_at}) —
 *       {@code insertable = false, updatable = false}.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "payments")
public class PaymentJpaEntity implements Persistable<UUID> {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    PaymentJpaEntity() {}

    public PaymentJpaEntity(UUID id, UUID orderId, UUID customerId, String status, BigDecimal amount,
                            String currency, long version, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Always {@code false} — see class javadoc; forces Spring Data JPA to always use {@code merge()}. */
    @Override
    public boolean isNew() {
        return false;
    }
}

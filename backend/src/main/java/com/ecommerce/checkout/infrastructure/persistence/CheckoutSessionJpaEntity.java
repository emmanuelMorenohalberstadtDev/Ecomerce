package com.ecommerce.checkout.infrastructure.persistence;

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
 * JPA entity mapping the {@code checkout_sessions} table
 * ({@code V005__create_checkout_schema.sql}).
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7 before
 *       INSERT (ADR-0002), matching {@code CartJpaEntity}/{@code StockReservationJpaEntity}.</li>
 *   <li><strong>Totals-only, no per-line child table:</strong> {@code checkout_sessions} carries
 *       aggregate snapshot columns only (subtotal/discount_total/shipping_fee/grand_total/
 *       expected_total/currency) — no {@code checkout_session_lines} table exists in the
 *       migration. This entity therefore has no collection mapping;
 *       {@code JpaCheckoutSessionRepository} always reconstitutes the domain's
 *       {@code RecalculatedTotal} with an empty {@code lines} list (see that class's and
 *       {@code RecalculatedTotal}'s javadoc — the per-line breakdown is transient, in-memory only).</li>
 *   <li>{@code @Version} maps {@code checkout_sessions.version} for optimistic locking
 *       (database-design.md §7) — a plain guarded {@code save} is sufficient here (unlike
 *       {@code StockReservationJpaEntity}'s header-only update; see
 *       {@code CheckoutSessionRepository}'s javadoc for why).</li>
 *   <li><strong>{@code implements Persistable<UUID>}, {@code isNew()} always {@code false}:</strong>
 *       same reasoning as {@code CartJpaEntity} — a manually-assigned (never-null) id AND a
 *       {@code @Version} field together would otherwise make Spring Data JPA misclassify a
 *       session being updated for the first time (version still {@code 0}) as "new" and
 *       re-attempt an INSERT. Forcing {@code isNew() == false} makes {@code save()} always call
 *       {@code entityManager.merge(...)}, which correctly performs an INSERT or UPDATE either way
 *       and still honours {@code @Version} for optimistic-lock checking on the UPDATE path.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "checkout_sessions")
public class CheckoutSessionJpaEntity implements Persistable<UUID> {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "cart_id", nullable = false, updatable = false)
    private UUID cartId;

    @Column(nullable = false)
    private String status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal;

    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingFee;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Column(name = "expected_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal expectedTotal;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    CheckoutSessionJpaEntity() {}

    public CheckoutSessionJpaEntity(UUID id, UUID customerId, UUID cartId, String status, UUID reservationId,
                                    BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingFee,
                                    BigDecimal grandTotal, BigDecimal expectedTotal, String currency,
                                    Instant paymentDeadline, String idempotencyKey, long version,
                                    Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.cartId = cartId;
        this.status = status;
        this.reservationId = reservationId;
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.shippingFee = shippingFee;
        this.grandTotal = grandTotal;
        this.expectedTotal = expectedTotal;
        this.currency = currency;
        this.paymentDeadline = paymentDeadline;
        this.idempotencyKey = idempotencyKey;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getCartId() { return cartId; }
    public String getStatus() { return status; }
    public UUID getReservationId() { return reservationId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public BigDecimal getExpectedTotal() { return expectedTotal; }
    public String getCurrency() { return currency; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Always {@code false} — see class javadoc; forces Spring Data JPA to always use {@code merge()}. */
    @Override
    public boolean isNew() {
        return false;
    }
}

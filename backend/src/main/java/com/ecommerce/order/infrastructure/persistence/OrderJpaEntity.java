package com.ecommerce.order.infrastructure.persistence;

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
 * JPA entity mapping the {@code orders} header table ({@code V006__create_order_schema.sql}).
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7 before
 *       INSERT (ADR-0002), matching {@code CheckoutSessionJpaEntity}.</li>
 *   <li><strong>No JPA relationship to {@code order_items}/{@code order_status_history}:</strong>
 *       deliberately NOT mapped as {@code @OneToMany} collections on this entity. Both child
 *       tables are write-once-per-row ({@code order_items}: inserted once at placement, never
 *       updated; {@code order_status_history}: append-only, one new row per transition) — modeling
 *       them as a merge-managed collection would force every subsequent header save to rebuild
 *       the full collection from the framework-free domain object, and since neither
 *       {@code LineSnapshot} nor {@code OrderStatusTransition} carries the DB-generated
 *       {@code bigint identity} id back into the domain layer, Hibernate's {@code merge()} would
 *       treat every rebuilt child as transient and re-INSERT it — silently duplicating
 *       {@code order_items} rows on every status transition and quadratically duplicating
 *       {@code order_status_history} rows. {@link JpaOrderRepository} instead persists items only
 *       once (at {@link JpaOrderRepository#create}) and status history one row at a time (at both
 *       {@code create} and {@code save}), each via its own targeted, independent Spring Data DAO —
 *       see that class's javadoc.</li>
 *   <li>{@code @Version} maps {@code orders.version} for optimistic locking
 *       (database-design.md §7); a plain guarded {@code save} (merge) is sufficient, matching
 *       {@code CheckoutSessionJpaEntity}.</li>
 *   <li><strong>{@code implements Persistable<UUID>}, {@code isNew()} always {@code false}:</strong>
 *       same reasoning as {@code CheckoutSessionJpaEntity} — a manually-assigned (never-null) id
 *       AND a {@code @Version} field together would otherwise make Spring Data JPA misclassify a
 *       row being updated for the first time (version still {@code 0}) as "new" and re-attempt an
 *       INSERT. Forcing {@code isNew() == false} makes {@code save()} always call
 *       {@code entityManager.merge(...)}, correctly performing an INSERT or UPDATE either way and
 *       still honouring {@code @Version} for optimistic-lock checking on the UPDATE path.</li>
 *   <li>{@code updated_at} is trigger-managed at the DB level ({@code trg_orders_updated_at}) —
 *       {@code insertable = false, updatable = false}, matching {@code CheckoutSessionJpaEntity}.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity implements Persistable<UUID> {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "items_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal itemsTotal;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal discountTotal;

    @Column(name = "shipping_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal shippingTotal;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal grandTotal;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "coupon_code", updatable = false)
    private String couponCode;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "placed_at", updatable = false)
    private Instant placedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    OrderJpaEntity() {}

    public OrderJpaEntity(UUID id, UUID customerId, String status, BigDecimal itemsTotal, BigDecimal discountTotal,
                          BigDecimal shippingTotal, BigDecimal grandTotal, String currency, String couponCode,
                          UUID reservationId, long version, Instant placedAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.itemsTotal = itemsTotal;
        this.discountTotal = discountTotal;
        this.shippingTotal = shippingTotal;
        this.grandTotal = grandTotal;
        this.currency = currency;
        this.couponCode = couponCode;
        this.reservationId = reservationId;
        this.version = version;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public BigDecimal getItemsTotal() { return itemsTotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getShippingTotal() { return shippingTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public String getCurrency() { return currency; }
    public String getCouponCode() { return couponCode; }
    public UUID getReservationId() { return reservationId; }
    public long getVersion() { return version; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Always {@code false} — see class javadoc; forces Spring Data JPA to always use {@code merge()}. */
    @Override
    public boolean isNew() {
        return false;
    }
}

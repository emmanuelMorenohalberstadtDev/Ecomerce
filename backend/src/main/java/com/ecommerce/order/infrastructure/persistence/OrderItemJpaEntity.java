package com.ecommerce.order.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity mapping one row of the {@code order_items} table
 * ({@code V006__create_order_schema.sql}).
 *
 * <p>{@code id} is {@code GenerationType.IDENTITY} (bigint identity) — an internal child row,
 * never client-addressed as an aggregate root. {@code orderId} is a plain column, not a
 * {@code @ManyToOne}/{@code @JoinColumn} relationship — see {@link OrderJpaEntity}'s class javadoc
 * for why no JPA relationship is modeled between the two. Rows are inserted once (at order
 * placement) and never updated afterward ({@code order_items} grants: {@code SELECT, INSERT}
 * only, rule 3 — a frozen {@code LineSnapshot}), so this entity has no update path at all.
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false, updatable = false)
    private int quantity;

    OrderItemJpaEntity() {}

    public OrderItemJpaEntity(UUID orderId, UUID productId, String productName, BigDecimal unitPrice,
                              String currency, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public int getQuantity() { return quantity; }
}

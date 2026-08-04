package com.ecommerce.cart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code cart_items} table — one row per {@code CartLine}.
 *
 * <p>{@code id} is {@code GenerationType.IDENTITY} (bigint identity, database-design.md §4) —
 * an internal child row, never client-addressed (V003 table comment: "PK is bigint identity --
 * internal child row, never client-addressed as an aggregate root"). {@code product_name},
 * {@code unit_price}, {@code currency} are the {@code ProductSnapshot} columns, frozen at
 * add-to-cart time. No {@code updated_at} column on this table (only {@code created_at}) —
 * matches V003 exactly; lines are replaced wholesale, not updated in place, by the adapter.
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "cart_items")
public class CartItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private CartJpaEntity cart;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean unavailable;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    CartItemJpaEntity() {}

    public CartItemJpaEntity(CartJpaEntity cart, UUID productId, String productName, BigDecimal unitPrice,
                             String currency, int quantity, boolean unavailable, Instant createdAt) {
        this.cart = cart;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.quantity = quantity;
        this.unavailable = unavailable;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public CartJpaEntity getCart() { return cart; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public int getQuantity() { return quantity; }
    public boolean isUnavailable() { return unavailable; }
    public Instant getCreatedAt() { return createdAt; }
}

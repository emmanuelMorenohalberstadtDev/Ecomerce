package com.ecommerce.inventory.domain.model;

import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.ecommerce.shared.id.ProductId;

import java.util.Objects;

/**
 * Aggregate root tracking available stock for one product (domain-model.md §2/§3).
 *
 * <p><strong>Not the path used to prevent oversell.</strong> The checkout-critical reserve/release
 * flows (rule 6) never load a {@code StockItem} at all — they call the atomic conditional
 * {@code UPDATE ... WHERE quantity_available >= :n} directly through
 * {@link com.ecommerce.inventory.domain.port.out.StockItemRepository#decrementIfSufficientStock}/
 * {@link com.ecommerce.inventory.domain.port.out.StockItemRepository#incrementAvailable}, with the
 * affected-row-count as the success signal (database-design.md §7: "modifying queries, not
 * load-modify-save ... loading StockItem entities to mutate them reopens the race the schema
 * closed"). This aggregate — and {@link #adjust}, its one behaviour — exists for the
 * <em>admin manual correction</em> path, which legitimately does load-modify-save under the
 * {@code version} optimistic lock (database-design.md §4/§7).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class StockItem {

    private final ProductId productId;
    private StockLevel available;
    private final long version;

    private StockItem(ProductId productId, StockLevel available, long version) {
        if (productId == null) {
            throw new IllegalArgumentException("ProductId must not be null");
        }
        if (available == null) {
            throw new IllegalArgumentException("StockLevel must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        this.productId = productId;
        this.available = available;
        this.version = version;
    }

    /**
     * Factory for reconstitution from persistence. There is deliberately no "create brand-new
     * stock item" factory here — see the inventory handoff report for the open question about
     * how a product's first {@code stock_items} row comes into existence; this aggregate only
     * models the correction of an <em>already-persisted</em> row.
     */
    public static StockItem reconstitute(ProductId productId, int quantityAvailable, long version) {
        return new StockItem(productId, StockLevel.of(quantityAvailable), version);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Applies a manual admin correction of {@code delta} units (positive = restock/correction up,
     * negative = correction down) with a mandatory {@code reason} (domain-model.md §1 inventory:
     * "Admin stock adjustments live here (audited)").
     *
     * @throws InsufficientStockException if the resulting quantity would go negative — the
     *         {@code ck_stock_items_quantity_available >= 0} backstop is never reached because
     *         this check rejects the operation first
     */
    public void adjust(int delta, AdjustmentReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        int newValue = this.available.value() + delta;
        if (newValue < 0) {
            throw new InsufficientStockException(
                    "Adjustment of " + delta + " would drive stock for product " + productId
                            + " below zero (current: " + this.available.value() + ")");
        }
        this.available = StockLevel.of(newValue);
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public ProductId getProductId() { return productId; }

    public StockLevel getAvailable() { return available; }

    public long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockItem stockItem)) return false;
        return Objects.equals(productId, stockItem.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
}

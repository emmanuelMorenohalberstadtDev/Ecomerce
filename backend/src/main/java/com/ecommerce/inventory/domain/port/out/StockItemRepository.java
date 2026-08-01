package com.ecommerce.inventory.domain.port.out;

import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.shared.id.ProductId;

import java.util.Optional;

/**
 * Domain port (outbound) for {@link StockItem} persistence and the two atomic, checkout-critical
 * quantity operations (database-design.md §3.4, §7).
 *
 * <p>Deliberately mixes two access styles on one port:
 * <ul>
 *   <li>{@link #findById}/{@link #save} — ordinary load-modify-save under the {@code version}
 *       optimistic lock, used only by the admin manual-adjustment path.</li>
 *   <li>{@link #decrementIfSufficientStock}/{@link #incrementAvailable} — single-statement
 *       conditional {@code UPDATE}s that never load a {@link StockItem} at all (database-design.md
 *       §7: "loading StockItem entities to mutate them reopens the race the schema closed"). This
 *       is the oversell-prevention path (rule 6) and the restock-on-release path (rule 7).</li>
 * </ul>
 *
 * <p>Lives in the domain layer; implemented by {@code JpaStockItemRepository} in infrastructure.
 * One repository per aggregate root (domain-model.md §2 ownership rules).
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and
 * {@code repository_interfaces_live_in_domain}.
 */
public interface StockItemRepository {

    Optional<StockItem> findById(ProductId productId);

    /** Load-modify-save write path (admin adjustment only) — see class javadoc. */
    StockItem save(StockItem stockItem);

    /**
     * Atomically decrements {@code quantity_available} by {@code quantity} if and only if the
     * current value is {@code >= quantity} — the single-statement conditional decrement that
     * prevents oversell (database-design.md §3.4):
     * {@code UPDATE stock_items SET quantity_available = quantity_available - :n WHERE product_id
     * = :p AND quantity_available >= :n}.
     *
     * @return {@code true} if exactly one row was updated (the decrement succeeded); {@code false}
     *         if zero rows were affected (insufficient stock — the caller's shortage signal)
     */
    boolean decrementIfSufficientStock(ProductId productId, int quantity);

    /**
     * Atomically increments {@code quantity_available} by {@code quantity} — the restock step of
     * a reservation release (rule 7). Unconditional: a release always succeeds if the
     * {@code stock_items} row exists (there is no upper bound to violate).
     */
    void incrementAvailable(ProductId productId, int quantity);
}

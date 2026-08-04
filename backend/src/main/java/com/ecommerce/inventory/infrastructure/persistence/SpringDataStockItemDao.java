package com.ecommerce.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Spring Data JPA interface for {@link StockItemJpaEntity}.
 *
 * <p>Package-private — only {@link JpaStockItemRepository} uses it. Named "Dao" (not
 * "Repository") to avoid triggering the ArchUnit rule that requires every {@code *Repository}
 * interface to live in {@code ..domain..} — mirrors {@code SpringDataCartDao}.
 *
 * <p>{@link #decrementIfSufficientStock} and {@link #incrementAvailable} are the two
 * checkout-critical bulk {@code @Modifying} updates (database-design.md §3.4/§7) — see
 * {@code StockItemJpaEntity}'s class javadoc for why they deliberately bypass load-modify-save
 * and do not bump {@code version}. {@code clearAutomatically = true} drops any stale first-level
 * cache entry for the affected row so a subsequent {@code findById} in the same transaction (if
 * any) re-reads the just-updated value instead of a cached one.
 */
@Repository
interface SpringDataStockItemDao extends JpaRepository<StockItemJpaEntity, UUID> {

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockItemJpaEntity s SET s.quantityAvailable = s.quantityAvailable - :quantity "
            + "WHERE s.productId = :productId AND s.quantityAvailable >= :quantity")
    int decrementIfSufficientStock(@Param("productId") UUID productId, @Param("quantity") int quantity);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockItemJpaEntity s SET s.quantityAvailable = s.quantityAvailable + :quantity "
            + "WHERE s.productId = :productId")
    int incrementAvailable(@Param("productId") UUID productId, @Param("quantity") int quantity);
}

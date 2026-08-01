package com.ecommerce.inventory.infrastructure.persistence;

import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.shared.id.ProductId;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link StockItemRepository} port.
 *
 * <p>{@link #save} is the load-modify-save path (admin adjustment only); it always builds a
 * brand-new detached {@link StockItemJpaEntity} from current domain state, mirroring
 * {@code JpaCartRepository#save} — {@code StockItemJpaEntity#isNew()} always returns
 * {@code false}, so {@code SpringDataStockItemDao.save(...)} always resolves to
 * {@code entityManager.merge(...)}, which correctly performs an INSERT or UPDATE either way.
 *
 * <p>{@link #decrementIfSufficientStock}/{@link #incrementAvailable} pass straight through to the
 * bulk {@code @Modifying} queries — no entity mapping involved on that path at all.
 */
public class JpaStockItemRepository implements StockItemRepository {

    private final SpringDataStockItemDao springDataStockItemDao;
    private final Clock clock;

    public JpaStockItemRepository(SpringDataStockItemDao springDataStockItemDao, Clock clock) {
        this.springDataStockItemDao = Objects.requireNonNull(springDataStockItemDao);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<StockItem> findById(ProductId productId) {
        return springDataStockItemDao.findById(productId.value()).map(this::toDomain);
    }

    @Override
    public StockItem save(StockItem stockItem) {
        StockItemJpaEntity entity = new StockItemJpaEntity(
                stockItem.getProductId().value(),
                stockItem.getAvailable().value(),
                stockItem.getVersion(),
                clock.instant()); // createdAt: ignored on UPDATE because updatable=false
        StockItemJpaEntity saved = springDataStockItemDao.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean decrementIfSufficientStock(ProductId productId, int quantity) {
        int rowsAffected = springDataStockItemDao.decrementIfSufficientStock(productId.value(), quantity);
        return rowsAffected == 1;
    }

    @Override
    public void incrementAvailable(ProductId productId, int quantity) {
        springDataStockItemDao.incrementAvailable(productId.value(), quantity);
    }

    private StockItem toDomain(StockItemJpaEntity entity) {
        return StockItem.reconstitute(
                ProductId.of(entity.getProductId()), entity.getQuantityAvailable(), entity.getVersion());
    }
}

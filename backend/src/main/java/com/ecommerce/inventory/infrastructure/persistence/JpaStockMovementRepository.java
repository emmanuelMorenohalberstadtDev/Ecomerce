package com.ecommerce.inventory.infrastructure.persistence;

import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;

import java.util.Objects;

/**
 * JPA adapter implementing the domain's {@link StockMovementRepository} port — insert-only,
 * mirroring catalog's {@code AdminAuditLogJpaAdapter} shape.
 */
public class JpaStockMovementRepository implements StockMovementRepository {

    private final SpringDataStockMovementDao springDataStockMovementDao;

    public JpaStockMovementRepository(SpringDataStockMovementDao springDataStockMovementDao) {
        this.springDataStockMovementDao = Objects.requireNonNull(springDataStockMovementDao);
    }

    @Override
    public void record(StockMovement movement) {
        StockMovementJpaEntity entity = new StockMovementJpaEntity(
                movement.productId().value(),
                movement.movementType().name(),
                movement.quantityDelta(),
                movement.reservationId() == null ? null : movement.reservationId().value(),
                movement.actorId(),
                movement.reason(),
                movement.occurredAt());
        springDataStockMovementDao.save(entity);
    }
}

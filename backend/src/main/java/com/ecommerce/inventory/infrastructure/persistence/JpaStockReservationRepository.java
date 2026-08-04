package com.ecommerce.inventory.infrastructure.persistence;

import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link StockReservationRepository} port.
 *
 * <p>{@link #create} is the only path that ever persists the lines collection — see
 * {@link StockReservationJpaEntity} and {@link StockReservationRepository} javadoc for why.
 * {@link #updateStatus} issues a header-only bulk update. Both {@link #findById} and
 * {@link #findHeldExpiredAsOf} join-fetch lines so the domain aggregate is always reconstituted
 * complete.
 */
public class JpaStockReservationRepository implements StockReservationRepository {

    private final SpringDataStockReservationDao springDataStockReservationDao;

    public JpaStockReservationRepository(SpringDataStockReservationDao springDataStockReservationDao) {
        this.springDataStockReservationDao = Objects.requireNonNull(springDataStockReservationDao);
    }

    @Override
    public StockReservation create(StockReservation reservation) {
        StockReservationJpaEntity entity = new StockReservationJpaEntity(
                reservation.getId().value(),
                reservation.getCheckoutSessionId().value(),
                reservation.getStatus().name(),
                reservation.getExpiresAt().isPresent() ? reservation.getExpiresAt().value() : null,
                reservation.getCreatedAt());

        List<StockReservationLineJpaEntity> lineEntities = reservation.getLines().stream()
                .map(line -> new StockReservationLineJpaEntity(
                        entity, line.productId().value(), line.quantity().value()))
                .toList();
        entity.addLines(lineEntities);

        StockReservationJpaEntity saved = springDataStockReservationDao.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<StockReservation> findById(ReservationId id) {
        return springDataStockReservationDao.findByIdFetchLines(id.value()).map(this::toDomain);
    }

    @Override
    public boolean updateStatus(ReservationId id, ReservationStatus newStatus) {
        return springDataStockReservationDao.updateStatus(id.value(), newStatus.name()) > 0;
    }

    @Override
    public List<StockReservation> findHeldExpiredAsOf(Instant now) {
        return springDataStockReservationDao.findHeldExpiredAsOf(now).stream().map(this::toDomain).toList();
    }

    private StockReservation toDomain(StockReservationJpaEntity entity) {
        List<ReservedLine> lines = entity.getLines().stream()
                .map(l -> new ReservedLine(ProductId.of(l.getProductId()), Quantity.of(l.getQuantity())))
                .toList();
        Expiry expiry = entity.getExpiresAt() == null ? Expiry.none() : Expiry.at(entity.getExpiresAt());
        return StockReservation.reconstitute(
                ReservationId.of(entity.getId()),
                CheckoutSessionId.of(entity.getCheckoutSessionId()),
                ReservationStatus.valueOf(entity.getStatus()),
                expiry,
                entity.getCreatedAt(),
                lines);
    }
}

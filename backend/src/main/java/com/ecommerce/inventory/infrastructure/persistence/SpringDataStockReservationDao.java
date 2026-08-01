package com.ecommerce.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link StockReservationJpaEntity}.
 *
 * <p>Package-private — only {@link JpaStockReservationRepository} uses it. Named "Dao" to avoid
 * the ArchUnit {@code *Repository}-in-domain rule, mirroring {@code SpringDataCartDao}.
 *
 * <p>{@link #updateStatus} is the only mutation path after initial creation — see
 * {@link StockReservationJpaEntity}'s class javadoc for why it deliberately never loads/saves the
 * lines collection. {@link #findHeldExpiredAsOf} uses {@code SELECT DISTINCT} because it returns
 * a {@code List} across potentially many reservations, each with a fetched line collection —
 * without {@code DISTINCT}, a reservation with N lines would appear N times in the result.
 * {@link #findByIdFetchLines} returns a single {@code Optional} result, matching
 * {@code SpringDataCartDao#findByIdFetchItems}'s established (undistincted) pattern for a
 * single-aggregate fetch-join query.
 */
@Repository
interface SpringDataStockReservationDao extends JpaRepository<StockReservationJpaEntity, UUID> {

    @Query("SELECT r FROM StockReservationJpaEntity r LEFT JOIN FETCH r.lines WHERE r.id = :id")
    Optional<StockReservationJpaEntity> findByIdFetchLines(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockReservationJpaEntity r SET r.status = :status WHERE r.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Query("SELECT DISTINCT r FROM StockReservationJpaEntity r LEFT JOIN FETCH r.lines "
            + "WHERE r.status = 'HELD' AND r.expiresAt IS NOT NULL AND r.expiresAt < :now")
    List<StockReservationJpaEntity> findHeldExpiredAsOf(@Param("now") Instant now);
}

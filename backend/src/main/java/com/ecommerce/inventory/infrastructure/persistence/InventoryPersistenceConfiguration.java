package com.ecommerce.inventory.infrastructure.persistence;

import com.ecommerce.inventory.application.port.AuditLogPort;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Persistence-layer wiring for the inventory context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private Spring Data DAO
 * interfaces without making them public — mirrors {@code CatalogPersistenceConfiguration} /
 * {@code CartPersistenceConfiguration} exactly.
 */
@Configuration
public class InventoryPersistenceConfiguration {

    @Bean
    public StockItemRepository stockItemRepository(SpringDataStockItemDao springDataStockItemDao, Clock clock) {
        return new JpaStockItemRepository(springDataStockItemDao, clock);
    }

    @Bean
    public StockReservationRepository stockReservationRepository(
            SpringDataStockReservationDao springDataStockReservationDao) {
        return new JpaStockReservationRepository(springDataStockReservationDao);
    }

    @Bean
    public StockMovementRepository stockMovementRepository(SpringDataStockMovementDao springDataStockMovementDao) {
        return new JpaStockMovementRepository(springDataStockMovementDao);
    }

    @Bean
    public AuditLogPort inventoryAuditLogPort(SpringDataInventoryAuditLogDao springDataInventoryAuditLogDao, Clock clock) {
        return new InventoryAuditLogJpaAdapter(springDataInventoryAuditLogDao, clock);
    }
}

package com.ecommerce.order.infrastructure.persistence;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.domain.OrderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Persistence-layer wiring for the order context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private Spring Data DAO
 * interfaces without making them public — mirrors {@code CheckoutPersistenceConfiguration}/
 * {@code InventoryPersistenceConfiguration} exactly.
 */
@Configuration
public class OrderPersistenceConfiguration {

    @Bean
    public OrderRepository orderRepository(SpringDataOrderDao springDataOrderDao,
                                           SpringDataOrderItemDao springDataOrderItemDao,
                                           SpringDataOrderStatusHistoryDao springDataOrderStatusHistoryDao) {
        return new JpaOrderRepository(springDataOrderDao, springDataOrderItemDao, springDataOrderStatusHistoryDao);
    }

    @Bean
    public AuditLogPort auditLogPort(SpringDataOrderAuditLogDao springDataOrderAuditLogDao, Clock clock) {
        return new OrderAuditLogJpaAdapter(springDataOrderAuditLogDao, clock);
    }
}

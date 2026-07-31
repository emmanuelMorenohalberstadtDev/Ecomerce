package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Persistence-layer wiring for the catalog context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private Spring Data DAO
 * interfaces without making them public — preserving the encapsulation that prevents other
 * packages from bypassing the adapter layer. Mirrors
 * {@code AuthPersistenceConfiguration} exactly.
 */
@Configuration
public class CatalogPersistenceConfiguration {

    @Bean
    public ProductRepository productRepository(SpringDataProductDao springDataProductDao, Clock clock) {
        return new JpaProductRepository(springDataProductDao, clock);
    }

    @Bean
    public CategoryRepository categoryRepository(SpringDataCategoryDao springDataCategoryDao, Clock clock) {
        return new JpaCategoryRepository(springDataCategoryDao, clock);
    }

    @Bean
    public AuditLogPort auditLogPort(SpringDataAdminAuditLogDao springDataAdminAuditLogDao, Clock clock) {
        return new AdminAuditLogJpaAdapter(springDataAdminAuditLogDao, clock);
    }
}

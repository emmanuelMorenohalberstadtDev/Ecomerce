package com.ecommerce.checkout.infrastructure.persistence;

import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Persistence-layer wiring for the checkout context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private
 * {@link SpringDataCheckoutSessionDao} without making it public. Mirrors
 * {@code CartPersistenceConfiguration}/{@code CatalogPersistenceConfiguration} exactly.
 */
@Configuration
public class CheckoutPersistenceConfiguration {

    @Bean
    public CheckoutSessionRepository checkoutSessionRepository(
            SpringDataCheckoutSessionDao springDataCheckoutSessionDao) {
        return new JpaCheckoutSessionRepository(springDataCheckoutSessionDao);
    }
}

package com.ecommerce.cart.infrastructure.persistence;

import com.ecommerce.cart.domain.port.out.CartRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Persistence-layer wiring for the cart context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private
 * {@link SpringDataCartDao} without making it public. Mirrors {@code CatalogPersistenceConfiguration}
 * exactly.
 */
@Configuration
public class CartPersistenceConfiguration {

    @Bean
    public CartRepository cartRepository(SpringDataCartDao springDataCartDao, Clock clock) {
        return new JpaCartRepository(springDataCartDao, clock);
    }
}

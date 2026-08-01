package com.ecommerce.cart.infrastructure.config;

import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort;
import com.ecommerce.cart.infrastructure.adapter.ProductCatalogAdapter;
import com.ecommerce.cart.infrastructure.security.GuestCartTokenGenerator;
import com.ecommerce.catalog.application.port.ProductLookupPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-persistence infrastructure wiring for the cart context: the guest-token issuer and the
 * cross-context catalog adapter. Mirrors the {@code *Configuration} pattern used by
 * {@code CatalogPersistenceConfiguration}/{@code CartPersistenceConfiguration}.
 */
@Configuration
public class CartInfrastructureConfiguration {

    @Bean
    public GuestCartTokenIssuer guestCartTokenIssuer() {
        return new GuestCartTokenGenerator();
    }

    @Bean
    public ProductCatalogPort productCatalogPort(ProductLookupPort productLookupPort) {
        return new ProductCatalogAdapter(productLookupPort);
    }
}

package com.ecommerce.cart.infrastructure.config;

import com.ecommerce.cart.application.port.CartReadPort;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort;
import com.ecommerce.cart.infrastructure.adapter.CartReadAdapter;
import com.ecommerce.cart.infrastructure.adapter.ProductCatalogAdapter;
import com.ecommerce.cart.infrastructure.security.GuestCartTokenGenerator;
import com.ecommerce.catalog.application.port.ProductLookupPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-persistence infrastructure wiring for the cart context: the guest-token issuer, the
 * cross-context catalog adapter, and cart's own outbound {@link CartReadPort} façade (ADR-0003
 * §Decision item 1 — the sanctioned crossing point for checkout's read of a customer's cart
 * lines). Mirrors the {@code *Configuration} pattern used by
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

    @Bean
    public CartReadPort cartReadPort(CartRepository cartRepository) {
        return new CartReadAdapter(cartRepository);
    }
}

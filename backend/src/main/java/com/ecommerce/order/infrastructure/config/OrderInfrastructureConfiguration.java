package com.ecommerce.order.infrastructure.config;

import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.domain.port.out.ProductCatalogPort;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.order.infrastructure.adapter.ProductCatalogAdapter;
import com.ecommerce.order.infrastructure.adapter.ReservationAdapter;
import com.ecommerce.order.infrastructure.security.SecurityContextCurrentActorAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-persistence infrastructure wiring for the order context: the two cross-context outbound
 * port adapters (ADR-0004) and {@link CurrentActorPort}. Mirrors
 * {@code InventoryInfrastructureConfiguration}'s exact style.
 *
 * <p>Every use case in this context is a plain {@code @Service}-annotated, component-scanned bean
 * (none needs a config-resolved value like checkout's payment-window {@code Duration}) — matching
 * {@code inventory.application.usecase.ReleaseReservationUseCase}/{@code CommitReservationUseCase},
 * neither of which appears in an explicit {@code @Bean} method either. Listeners
 * ({@code PlaceOrderFromCheckoutListener}, {@code FailOrderFromCheckoutExpiryListener}) are
 * {@code @Component}-scanned the same way.
 */
@Configuration
public class OrderInfrastructureConfiguration {

    @Bean
    public CurrentActorPort currentActorPort() {
        return new SecurityContextCurrentActorAdapter();
    }

    /**
     * Cross-context outbound façade (ADR-0004 §Decision item 3) — the sanctioned crossing point
     * for order's reservation commit/release.
     */
    @Bean
    public ReservationPort reservationPort(StockReservationPort stockReservationPort) {
        return new ReservationAdapter(stockReservationPort);
    }

    /**
     * Cross-context outbound façade (ADR-0004 §Decision item 1) — the sanctioned crossing point
     * for order's product-name resolution at placement time.
     */
    @Bean
    public ProductCatalogPort productCatalogPort(ProductLookupPort productLookupPort) {
        return new ProductCatalogAdapter(productLookupPort);
    }
}

package com.ecommerce.order.infrastructure.config;

import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.order.application.port.CurrentActorPort;
import com.ecommerce.order.application.port.OrderPaymentPort;
import com.ecommerce.order.application.usecase.GetOrderForPaymentUseCase;
import com.ecommerce.order.application.usecase.MarkOrderPaidFromPaymentUseCase;
import com.ecommerce.order.domain.port.out.ProductCatalogPort;
import com.ecommerce.order.domain.port.out.ReservationPort;
import com.ecommerce.order.infrastructure.adapter.OrderPaymentAdapter;
import com.ecommerce.order.infrastructure.adapter.ProductCatalogAdapter;
import com.ecommerce.order.infrastructure.adapter.ReservationAdapter;
import com.ecommerce.order.infrastructure.security.SecurityContextCurrentActorAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-persistence infrastructure wiring for the order context: the cross-context outbound port
 * adapters (ADR-0004, ADR-0005) and {@link CurrentActorPort}. Mirrors
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
    public CurrentActorPort orderCurrentActorPort() {
        return new SecurityContextCurrentActorAdapter();
    }

    /**
     * Cross-context outbound façade (ADR-0004 §Decision item 3) — the sanctioned crossing point
     * for order's reservation commit/release.
     */
    @Bean
    public ReservationPort orderReservationPort(StockReservationPort stockReservationPort) {
        return new ReservationAdapter(stockReservationPort);
    }

    /**
     * Cross-context outbound façade (ADR-0004 §Decision item 1) — the sanctioned crossing point
     * for order's product-name resolution at placement time.
     */
    @Bean
    public ProductCatalogPort orderProductCatalogPort(ProductLookupPort productLookupPort) {
        return new ProductCatalogAdapter(productLookupPort);
    }

    /**
     * Order's first-ever producer-side façade (ADR-0005 §Decision item 1) — the sanctioned
     * crossing point payment uses to read a customer's order and drive its
     * {@code PLACED -> PAID} transition on a successful capture.
     */
    @Bean
    public OrderPaymentPort orderPaymentPort(GetOrderForPaymentUseCase getOrderForPaymentUseCase,
                                             MarkOrderPaidFromPaymentUseCase markOrderPaidFromPaymentUseCase) {
        return new OrderPaymentAdapter(getOrderForPaymentUseCase, markOrderPaidFromPaymentUseCase);
    }
}

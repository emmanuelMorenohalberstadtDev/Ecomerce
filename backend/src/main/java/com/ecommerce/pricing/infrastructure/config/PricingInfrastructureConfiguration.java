package com.ecommerce.pricing.infrastructure.config;

import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.pricing.application.port.PriceCalculationPort;
import com.ecommerce.pricing.application.usecase.CalculateEffectivePriceUseCase;
import com.ecommerce.pricing.domain.model.PriceCalculator;
import com.ecommerce.pricing.domain.port.out.ProductCatalogPort;
import com.ecommerce.pricing.domain.port.out.PromotionPolicyPort;
import com.ecommerce.pricing.infrastructure.adapter.CatalogPriceAdapter;
import com.ecommerce.pricing.infrastructure.adapter.NoDiscountPromotionPolicyAdapter;
import com.ecommerce.pricing.infrastructure.adapter.PriceCalculationAdapter;
import com.ecommerce.shared.money.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Wires the pricing context's outbound ports, its pure domain service, and its single use case.
 *
 * <p>Mirrors {@code CartInfrastructureConfiguration}'s explicit {@code @Bean}-method style (not
 * catalog's narrower {@code @Component}-on-adapter style), because the flat shipping fee — a
 * policy constant — must be resolved from Spring config ({@code @Value}) here and handed into
 * {@link CalculateEffectivePriceUseCase}'s constructor as an already-built {@link Money}. The use
 * case itself must never read {@code @Value} directly: that would make {@code application} depend
 * on a Spring annotation, violating ArchitectureTest rule 4 (application must not depend on
 * infrastructure).
 */
@Configuration
public class PricingInfrastructureConfiguration {

    @Bean
    public ProductCatalogPort pricingProductCatalogPort(ProductLookupPort productLookupPort) {
        return new CatalogPriceAdapter(productLookupPort);
    }

    @Bean
    public PromotionPolicyPort promotionPolicyPort() {
        return new NoDiscountPromotionPolicyAdapter();
    }

    @Bean
    public PriceCalculator priceCalculator() {
        return new PriceCalculator();
    }

    @Bean
    public CalculateEffectivePriceUseCase calculateEffectivePriceUseCase(
            ProductCatalogPort productCatalogPort,
            PromotionPolicyPort promotionPolicyPort,
            PriceCalculator priceCalculator,
            @Value("${pricing.shipping-fee:5.00}") BigDecimal shippingFeeAmount,
            @Value("${pricing.currency:USD}") String currency) {
        return new CalculateEffectivePriceUseCase(productCatalogPort, promotionPolicyPort, priceCalculator,
                new Money(shippingFeeAmount, currency));
    }

    /**
     * Cross-context outbound façade (ADR-0003 §Decision item 1) — the sanctioned crossing point
     * for checkout's price recalculation.
     */
    @Bean
    public PriceCalculationPort priceCalculationPort(CalculateEffectivePriceUseCase calculateEffectivePriceUseCase) {
        return new PriceCalculationAdapter(calculateEffectivePriceUseCase);
    }
}

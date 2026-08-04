package com.ecommerce.checkout.infrastructure.config;

import com.ecommerce.cart.application.port.CartReadPort;
import com.ecommerce.checkout.application.usecase.ConfirmRecalculatedTotalUseCase;
import com.ecommerce.checkout.application.usecase.StartCheckoutUseCase;
import com.ecommerce.checkout.domain.port.out.CartLinesPort;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.checkout.domain.port.out.PriceQuotePort;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import com.ecommerce.checkout.infrastructure.adapter.CartLinesAdapter;
import com.ecommerce.checkout.infrastructure.adapter.PriceQuoteAdapter;
import com.ecommerce.checkout.infrastructure.adapter.ReservationAdapter;
import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.pricing.application.port.PriceCalculationPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the checkout context's three cross-context outbound port adapters (ADR-0003 §Decision
 * item 2) and its application-layer use cases.
 *
 * <p>Each adapter's only dependency is the producer context's {@code application.port} façade
 * bean, constructor-injected — the sanctioned crossing point per
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}. Mirrors
 * {@code PricingInfrastructureConfiguration}'s explicit {@code @Bean}-method style: the payment
 * window {@link java.time.Duration} is resolved from {@link CheckoutProperties} here and handed
 * into the use case constructors as a plain value — the use cases themselves never read
 * {@code @ConfigurationProperties} directly, which would make {@code application} depend on
 * {@code infrastructure} (ArchitectureTest rule 4).
 *
 * <p>{@code StartCheckoutUseCase} and {@code ConfirmRecalculatedTotalUseCase} are wired here
 * explicitly (not {@code @Service}-annotated — see their own javadoc) precisely because they need
 * that config-resolved {@code Duration}. {@code ExpireCheckoutSessionUseCase} and
 * {@code ExpirePaymentWindowUseCase} need no such value, so they stay plain
 * {@code @Service}-annotated, component-scanned beans — mirroring
 * {@code inventory.application.usecase.ReleaseReservationUseCase}/{@code ExpireReservationsUseCase},
 * neither of which appears in {@code InventoryInfrastructureConfiguration} either. Declaring an
 * explicit {@code @Bean} method for an already-{@code @Service}-annotated class would register it
 * twice.
 */
@Configuration
@EnableConfigurationProperties(CheckoutProperties.class)
public class CheckoutInfrastructureConfiguration {

    @Bean
    public CartLinesPort cartLinesPort(CartReadPort cartReadPort) {
        return new CartLinesAdapter(cartReadPort);
    }

    @Bean
    public PriceQuotePort priceQuotePort(PriceCalculationPort priceCalculationPort) {
        return new PriceQuoteAdapter(priceCalculationPort);
    }

    @Bean
    public ReservationPort checkoutReservationPort(StockReservationPort stockReservationPort) {
        return new ReservationAdapter(stockReservationPort);
    }

    @Bean
    public StartCheckoutUseCase startCheckoutUseCase(CartLinesPort cartLinesPort,
                                                      PriceQuotePort priceQuotePort,
                                                      ReservationPort reservationPort,
                                                      CheckoutSessionRepository checkoutSessionRepository,
                                                      ApplicationEventPublisher eventPublisher,
                                                      Clock clock,
                                                      CheckoutProperties checkoutProperties) {
        return new StartCheckoutUseCase(cartLinesPort, priceQuotePort, reservationPort,
                checkoutSessionRepository, eventPublisher, clock, checkoutProperties.paymentWindow());
    }

    @Bean
    public ConfirmRecalculatedTotalUseCase confirmRecalculatedTotalUseCase(
            CheckoutSessionRepository checkoutSessionRepository,
            CartLinesPort cartLinesPort,
            PriceQuotePort priceQuotePort,
            ReservationPort reservationPort,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            CheckoutProperties checkoutProperties) {
        return new ConfirmRecalculatedTotalUseCase(checkoutSessionRepository, cartLinesPort, priceQuotePort,
                reservationPort, eventPublisher, clock, checkoutProperties.paymentWindow());
    }
}

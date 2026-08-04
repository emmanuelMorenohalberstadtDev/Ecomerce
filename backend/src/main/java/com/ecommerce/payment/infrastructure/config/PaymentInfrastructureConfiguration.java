package com.ecommerce.payment.infrastructure.config;

import com.ecommerce.order.application.port.OrderPaymentPort;
import com.ecommerce.payment.domain.port.out.OrderPort;
import com.ecommerce.payment.domain.port.out.PaymentGatewayPort;
import com.ecommerce.payment.infrastructure.adapter.OrderAdapter;
import com.ecommerce.payment.infrastructure.adapter.SimulatedPaymentGatewayAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-persistence infrastructure wiring for the payment context: the cross-context outbound port
 * adapter (ADR-0005) and the simulated PSP adapter. Mirrors
 * {@code OrderInfrastructureConfiguration}'s exact style.
 *
 * <p>Every use case in this context is a plain {@code @Service}-annotated, component-scanned bean
 * — none needs a config-resolved value.
 */
@Configuration
public class PaymentInfrastructureConfiguration {

    /**
     * Cross-context outbound façade (ADR-0005 §Decision item 1) — the sanctioned crossing point
     * for payment's order reads and {@code PLACED -> PAID} transitions.
     */
    @Bean
    public OrderPort orderPort(OrderPaymentPort orderPaymentPort) {
        return new OrderAdapter(orderPaymentPort);
    }

    /** v1's only {@link PaymentGatewayPort} implementation — simulated PSP (non-goal: real PSP). */
    @Bean
    public PaymentGatewayPort paymentGatewayPort() {
        return new SimulatedPaymentGatewayAdapter();
    }
}

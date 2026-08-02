package com.ecommerce.inventory.infrastructure.config;

import com.ecommerce.inventory.application.port.CurrentActorPort;
import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.inventory.application.usecase.CommitReservationUseCase;
import com.ecommerce.inventory.application.usecase.ReleaseReservationUseCase;
import com.ecommerce.inventory.application.usecase.ReserveStockUseCase;
import com.ecommerce.inventory.infrastructure.adapter.StockReservationAdapter;
import com.ecommerce.inventory.infrastructure.security.SecurityContextCurrentActorAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-persistence infrastructure wiring for the inventory context. Mirrors the
 * {@code *Configuration} pattern used by {@code CartInfrastructureConfiguration}.
 */
@Configuration
public class InventoryInfrastructureConfiguration {

    @Bean
    public CurrentActorPort currentActorPort() {
        return new SecurityContextCurrentActorAdapter();
    }

    /**
     * Cross-context outbound façade (ADR-0003 §Decision item 1; ADR-0004 §Decision item 2) — the
     * sanctioned crossing point for checkout's stock reservation/release and order's reservation
     * commit/release.
     */
    @Bean
    public StockReservationPort stockReservationPort(ReserveStockUseCase reserveStockUseCase,
                                                      ReleaseReservationUseCase releaseReservationUseCase,
                                                      CommitReservationUseCase commitReservationUseCase) {
        return new StockReservationAdapter(reserveStockUseCase, releaseReservationUseCase, commitReservationUseCase);
    }
}

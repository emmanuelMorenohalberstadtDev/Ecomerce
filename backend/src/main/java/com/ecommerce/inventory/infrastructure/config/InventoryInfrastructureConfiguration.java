package com.ecommerce.inventory.infrastructure.config;

import com.ecommerce.inventory.application.port.CurrentActorPort;
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
}

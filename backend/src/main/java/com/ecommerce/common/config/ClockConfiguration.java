package com.ecommerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a single {@link Clock} bean for the entire application.
 *
 * <p>Injecting {@code Clock} rather than calling {@code Instant.now()} or
 * {@code LocalDate.now()} directly makes every time-dependent use case deterministic in tests:
 * the test substitutes a {@link Clock#fixed fixed clock} and the code under test never touches
 * the system clock.
 *
 * <p>All use cases and domain services that need the current time must declare a {@code Clock}
 * constructor parameter — no static calls to {@code Instant.now()}.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

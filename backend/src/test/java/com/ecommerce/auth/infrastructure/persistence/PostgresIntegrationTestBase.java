package com.ecommerce.auth.infrastructure.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Singleton Testcontainers PostgreSQL container shared across all integration tests in the JVM.
 *
 * <p>Using a static container that is started once per JVM run — Testcontainers keeps it
 * alive through the JUnit lifecycle via {@code Testcontainers.REUSE_ENABLE} semantics.
 * Each test class must be annotated with {@code @Testcontainers} and extend this base
 * (or use it via composition via the static methods).
 *
 * <p>The {@link #configureProperties} method wires the container's JDBC coordinates into
 * the Spring {@code DataSource}, so the application-test profile (Flyway disabled) is
 * overridden and Flyway runs here against the real PostgreSQL instance.
 *
 * <p>Test isolation is achieved via {@code @Transactional} on each test class — Spring
 * rolls back after each test, leaving the schema clean for the next one.
 */
public abstract class PostgresIntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Enable Flyway against the Testcontainer — the test profile disables it by default
        registry.add("spring.flyway.enabled", () -> "true");
        // JWT secret for integration context — minimum 32 chars
        registry.add("jwt.secret", () -> "test-secret-key-for-integration-tests-32chars!!");
        registry.add("jwt.access-token-expiry-minutes", () -> "10");
    }
}

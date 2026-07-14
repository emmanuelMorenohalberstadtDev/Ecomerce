package com.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the skeleton actually runs: context boots against real PostgreSQL,
 * Flyway executes, the security chain loads, health is public and everything
 * else is denied by default (security-architecture.md §3).
 */
class ApplicationBootIT extends IntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void shouldExposeHealthPublicly_andReportUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void shouldDenyUnknownEndpointsByDefault_withProblemDetail() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/anything", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(response.getBody()).contains("authentication-required");
    }

    @Test
    void shouldEchoCorrelationIdHeader() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }
}

package com.ecommerce.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Baseline chain: stateless, deny-by-default (security-architecture.md §3).
 * 401 = not authenticated, 403 = authenticated but not allowed - never conflated,
 * both emitted as RFC 9457 problem details (api-guidelines.md §3).
 * Only actuator health and the OpenAPI spec are public in the skeleton;
 * the JWT filter and auth endpoints arrive with the auth feature.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String TYPE_BASE = "https://api.ecommerce.dev/problems/";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                // stateless bearer API: no session cookie to forge (security-architecture §2)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemDetail401())
                        .accessDeniedHandler(problemDetail403()))
                .build();
    }

    private AuthenticationEntryPoint problemDetail401() {
        return (request, response, ex) -> writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED,
                "authentication-required", "Authentication is required to access this resource");
    }

    private AccessDeniedHandler problemDetail403() {
        return (request, response, ex) -> writeProblem(response, HttpServletResponse.SC_FORBIDDEN,
                "access-denied", "You do not have permission to perform this operation");
    }

    private void writeProblem(HttpServletResponse response, int status, String type, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"%s%s","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(TYPE_BASE, type, type.replace('-', ' '), status, detail));
    }
}

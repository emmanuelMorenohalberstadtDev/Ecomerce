package com.ecommerce.auth.infrastructure.config;

import com.ecommerce.auth.infrastructure.security.JwtAuthenticationFilter;
import com.ecommerce.auth.infrastructure.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security filter chain for the ecommerce API.
 *
 * <p>Implements security-architecture §2–§3 verbatim:
 * <ul>
 *   <li>Stateless (no HTTP sessions).</li>
 *   <li>CSRF disabled — stateless Bearer token API; see §2.5 note about refresh/logout.</li>
 *   <li>Auth endpoints public; catalog GETs public; admin requires ADMIN role; all else
 *       requires authentication (deny by default).</li>
 *   <li>JWT filter extracts + validates Bearer tokens, populates SecurityContext.</li>
 *   <li>401 and 403 responses follow RFC 9457 Problem Details (§2.6, §3.3).</li>
 * </ul>
 *
 * <p>BCrypt strength 12 (cost factor) balances security vs login latency budget.
 * The security-architecture specifies ≥10; 12 is the chosen operating point.
 *
 * <p>Note: §2.5 requires Origin-header validation on refresh/logout endpoints as a
 * second layer after SameSite=Strict. In v1 this is implemented in the controller
 * via an {@code Origin} header check. It is NOT a CSRF token — CSRF tokens are
 * session-based; here we validate Origin to confirm the request came from the
 * known SPA origin. TODO: extract to a dedicated filter in a follow-up.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AuthSecurityConfiguration {

    private final JwtTokenService jwtTokenService;

    public AuthSecurityConfiguration(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // No HTTP sessions — stateless Bearer token API
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF disabled for stateless API (Bearer tokens provide the CSRF mitigation
                // for all endpoints except cookie-authenticated refresh/logout — those use
                // Origin validation in the controller per §2.5)
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints: public (rate-limited at nginx)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Catalog reads: public (anonymous browsing)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        // Actuator health: public (load balancer checks)
                        .requestMatchers("/actuator/health").permitAll()
                        // Admin surface: requires ADMIN role at the route level;
                        // fine-grained @PreAuthorize on use cases is the second layer (§3.2)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Everything else: authenticated; ownership enforced per use case (§3.2)
                        .anyRequest().authenticated()
                )

                // JWT filter runs before Spring's UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

                // 401 and 403 handlers emit RFC 9457 Problem Details (security §2.6, §3.3)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("""
                                    {"type":"https://api.ecommerce.dev/problems/unauthorized",\
                                    "title":"Unauthorized","status":401,\
                                    "detail":"Authentication required"}""");
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(403);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("""
                                    {"type":"https://api.ecommerce.dev/problems/forbidden",\
                                    "title":"Forbidden","status":403,\
                                    "detail":"Insufficient permissions"}""");
                        })
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * The filter is constructed here (not as a @Component) so it is under explicit
     * lifecycle control and not picked up by Spring Boot's auto-registration which
     * would add it to the Servlet filter chain a second time.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenService);
    }
}

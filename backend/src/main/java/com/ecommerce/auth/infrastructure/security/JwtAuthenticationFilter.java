package com.ecommerce.auth.infrastructure.security;

import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.infrastructure.security.JwtTokenService.JwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts and validates the JWT Bearer token from the {@code Authorization} header
 * and populates the {@link SecurityContextHolder}.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>No 401 is returned here. If the token is missing or invalid the filter simply
 *       does not set authentication — Spring Security's {@code AuthorizationFilter}
 *       downstream decides the response. This keeps the filter stateless and testable.</li>
 *   <li>Spring roles use the {@code ROLE_} prefix convention so the config can use
 *       {@code hasRole("ADMIN")} (which prepends {@code ROLE_}) instead of
 *       {@code hasAuthority("ROLE_ADMIN")}.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            JwtClaims claims = jwtTokenService.validateAndExtract(token);

            // Build a Spring Security authority using the ROLE_ convention
            String roleAuthority = "ROLE_" + claims.role().name();
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            claims.userId(),
                            null, // credentials not needed post-authentication
                            List.of(new SimpleGrantedAuthority(roleAuthority))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (TokenExpiredException e) {
            log.debug("Expired JWT on {}: {}", request.getRequestURI(), e.getMessage());
            // Do not set authentication; let Spring Security produce 401
        } catch (IllegalArgumentException e) {
            log.debug("Invalid JWT on {}: {}", request.getRequestURI(), e.getMessage());
            // Do not set authentication; let Spring Security produce 401
        }

        filterChain.doFilter(request, response);
    }
}

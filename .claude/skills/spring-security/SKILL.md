---
name: spring-security
description: Spring Security 6 configuration for a stateless JWT-protected REST API — filter chain, authorization rules, method security, and CORS.
---

# Spring Security

## Purpose

Configure Spring Security 6 so every request is authenticated and authorized explicitly, statelessly, with deny-by-default.

## When to Use

Configuring the security filter chain, adding endpoint rules, method-level checks, CORS, or wiring the JWT filter (token design itself: skill `jwt`).

## Rules

1. Stateless API: `SessionCreationPolicy.STATELESS`; CSRF disabled *only because* no cookie-based session auth exists — if auth ever moves to cookies, CSRF protection returns.
2. Deny by default: the chain ends in `.anyRequest().authenticated()` (or `denyAll` for unmatched); public endpoints are an explicit, short allowlist (`/api/v1/auth/**`, catalog reads, actuator health).
3. Authorization rules live in one place — the `SecurityFilterChain` for URL coarse rules, `@PreAuthorize` for method rules. Both mirror security-engineer's auth decision table.
4. Ownership checks are code, not annotations alone: `@PreAuthorize("hasRole('CUSTOMER')")` + use case verifying `order.belongsTo(principal)` — role checks never suffice for user-scoped resources.
5. The JWT filter validates and populates `SecurityContext`; it never writes responses beyond 401 via the `AuthenticationEntryPoint`. 401 = not authenticated, 403 = authenticated but not allowed — never conflate.
6. CORS configured explicitly for the frontend origin(s) from properties; no `*` with credentials.
7. Password storage: `BCryptPasswordEncoder` (or delegating encoder), strength per security-engineer.

## Examples

```java
@Bean
SecurityFilterChain api(HttpSecurity http, JwtAuthFilter jwt) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .exceptionHandling(e -> e
            .authenticationEntryPoint(problemDetail401())
            .accessDeniedHandler(problemDetail403()))
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

## Best Practices

- Emit 401/403 as RFC 9457 problem details via the entry point / denied handler so error contract stays uniform.
- `@EnableMethodSecurity` and put `@PreAuthorize` on use cases (application layer), not controllers — rules follow the operation, not the route.
- Test the chain: every row of the auth decision table gets an authZ test (anonymous / wrong role / wrong owner).
- Keep role names in one constants/enum source; `hasRole("ADMIN")` strings scattered = drift.

## Common Mistakes

- `permitAll` on a path "temporarily" during development — it ships.
- Confusing `hasRole("ADMIN")` (adds `ROLE_` prefix) with `hasAuthority("ADMIN")` — pick one convention, document it.
- Doing authorization in controllers with `if (user.isAdmin())` — invisible to audits.
- Disabling security in the `test` profile instead of testing with it on (use test JWTs / `@WithMockUser` deliberately).

## References

- Spring Security 6 reference; see skills `jwt`, `authorization`, `authentication`, `owasp`

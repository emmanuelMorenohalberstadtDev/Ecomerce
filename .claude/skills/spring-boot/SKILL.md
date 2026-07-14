---
name: spring-boot
description: Spring Boot 3 project conventions — configuration, profiles, dependency injection, bean design, and application structure for this ecommerce.
---

# Spring Boot

## Purpose

Use Spring Boot 3 (Jakarta, Java 21) as plumbing that stays out of the domain: configuration, wiring, and web/persistence infrastructure done the boring, correct way.

## When to Use

Bootstrapping modules, adding configuration, wiring beans, choosing starters, handling profiles/environments.

## Rules

1. Constructor injection only; fields `final`. No `@Autowired` on fields, no setter injection.
2. Configuration binds to typed records: `@ConfigurationProperties(prefix = "app.cart")` on a record, validated with `@Validated` — never scattered `@Value` strings.
3. Profiles: `dev`, `test`, `prod`. Environment differences are *values* (in `application-<profile>.yml` + env vars), never conditional code paths.
4. Secrets come from environment variables — no secret has a default in any yml.
5. One starter per real need; every new starter is a dependency requiring architect approval.
6. `@Component`-scanning stays within the app package; explicit `@Bean` methods in `@Configuration` classes for adapters and third-party wiring.
7. Startup must fail fast on invalid config (`@Validated` properties, Flyway `validate`), not limp into runtime errors.

## Examples

```java
@Validated
@ConfigurationProperties(prefix = "app.cart")
public record CartProperties(
    @NotNull Duration guestTtl,
    @Positive int maxItems
) {}
```

```yaml
# application.yml — safe defaults; application-prod.yml overrides via env
app:
  cart:
    guest-ttl: 7d
    max-items: 100
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway owns the schema, always
    open-in-view: false     # mandatory
```

## Best Practices

- `spring.jpa.open-in-view: false` from day one — OSIV hides N+1 and holds connections through rendering.
- Use `@ConditionalOnProperty` for optional integrations (email sender on/off in dev) instead of profile-specific beans where possible.
- Actuator: expose `health` (and `metrics` internally); never expose `env`/`configprops` publicly.
- Keep `@SpringBootApplication` class empty — no logic in the main class.
- Virtual threads (`spring.threads.virtual.enabled: true`) considered via ADR, not toggled casually.

## Common Mistakes

- Business logic in `@Component` "helpers" wired everywhere — that's the domain leaking into plumbing.
- Profile-conditional business behavior (`if (env.equals("prod"))`).
- `@Transactional` sprinkled on controllers or entire `@Service` classes (see skill `transactions`).
- Depending on bean initialization order implicitly; needing `@DependsOn` usually signals a design smell.
- Copying starter lists from tutorials — audit every dependency.

## References

- Spring Boot 3 reference docs (docs.spring.io)
- See skills `clean-architecture`, `jpa`, `transactions`, `exception-handling`

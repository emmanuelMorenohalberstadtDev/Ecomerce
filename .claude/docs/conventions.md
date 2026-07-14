# Conventions

Shared naming, structure, and workflow conventions. All agents produce artifacts that comply with this document.

## Git

### Branches

```
main                    # always releasable
develop                 # integration branch
feature/<ticket>-<slug> # e.g. feature/ECOM-42-cart-merge
fix/<ticket>-<slug>
refactor/<slug>
hotfix/<slug>
```

### Conventional Commits

```
<type>(<scope>): <subject in imperative, lowercase, no period>

[optional body: what & why, wrapped at 72 chars]

[optional footer: BREAKING CHANGE:, Closes #123]
```

- Types: `feat` `fix` `refactor` `test` `docs` `chore` `perf` `ci` `build`
- Scopes: `catalog` `cart` `checkout` `order` `payment` `inventory` `pricing` `promo` `auth` `user` `infra` `ui`
- One logical change per commit. Never mix refactor with feature in the same commit.

### Semantic Versioning

`MAJOR.MINOR.PATCH` — breaking API change / backward-compatible feature / backward-compatible fix.

## Backend (Java 21 / Spring Boot 3)

### Package structure (Clean Architecture, per bounded context)

```
com.ecommerce.<context>
├── domain/          # entities, value objects, domain services, domain events, repository interfaces
├── application/     # use cases (one class per use case), ports, application DTOs
├── infrastructure/  # JPA adapters, external clients, config
└── presentation/    # REST controllers, request/response DTOs, mappers
```

### Naming

| Artifact | Convention | Example |
|----------|-----------|---------|
| Use case | `<Verb><Noun>UseCase` | `AddItemToCartUseCase` |
| Controller | `<Resource>Controller` | `CartController` |
| Repository port | `<Entity>Repository` | `OrderRepository` |
| JPA adapter | `Jpa<Entity>Repository` | `JpaOrderRepository` |
| Request DTO | `<Action><Resource>Request` | `CreateOrderRequest` |
| Response DTO | `<Resource>Response` | `OrderResponse` |
| Domain event | `<Noun><PastTenseVerb>Event` | `OrderPlacedEvent` |
| Exception | `<Problem>Exception` | `InsufficientStockException` |

- DTOs are Java `record`s. Money is `BigDecimal` + currency, never `double`.
- Constructor injection only. No field `@Autowired`.

## Frontend (Angular 21)

### Folder structure

```
src/app/
├── core/            # singleton services, interceptors, guards
├── shared/          # reusable standalone components, pipes, directives
├── features/<name>/ # feature area: components, services, models, routes
└── layout/          # shell, header, footer, nav
```

### Naming & rules

- Standalone components only; `ChangeDetectionStrategy.OnPush` everywhere.
- Files: `cart-summary.component.ts`, `cart.service.ts`, `order.model.ts`, `cart.routes.ts`.
- Selectors: `app-` prefix, kebab-case.
- State: signals first; RxJS only for event streams and HTTP composition (see skills `angular-signals`, `rxjs`).
- Use native control flow (`@if`, `@for`, `@defer`), `inject()` over constructor injection.

## Database (PostgreSQL)

- Tables: plural `snake_case` (`order_items`). Columns: `snake_case`.
- PKs: `id` (UUID or `bigint identity` — decided per ADR). FKs: `<entity>_id`.
- Indexes: `idx_<table>_<cols>`. Unique: `uq_<table>_<cols>`. Checks: `ck_<table>_<rule>`.
- Every table: `created_at`, `updated_at` (`timestamptz`).
- Migrations: `V<version>__<snake_case_description>.sql` (e.g. `V003__add_cart_expiration.sql`). Never edit an applied migration.

## REST API

- Base path `/api/v1`. Nouns, plural: `/api/v1/orders/{id}/items`.
- Pagination: `?page=0&size=20&sort=createdAt,desc` → response wraps `content`, `page`, `totalElements`.
- Errors: RFC 9457 Problem Details (`application/problem+json`).
- Full contract rules: skill `rest-api`.

## Documentation

- ADRs in `docs/adr/NNNN-<slug>.md`, numbered sequentially, never deleted (superseded instead).
- Every public API change updates the OpenAPI spec in the same PR.

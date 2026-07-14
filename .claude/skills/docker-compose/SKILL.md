---
name: docker-compose
description: Compose environments for dev/test/prod-like — service topology, healthcheck-gated startup, env-based config, volumes, and profiles.
---

# Docker Compose

## Purpose

`docker compose up` from a clean clone yields a fully working stack — the compose file *is* the executable documentation of the system's topology.

## When to Use

Defining/changing the local stack, adding services, wiring environment config, debugging startup ordering.

## Rules

1. **Topology**: `db` (postgres:16-alpine, pinned = prod version), `backend`, `frontend` (nginx serving the SPA + proxying `/api`). Additions (mail-catcher, pgadmin) go behind **profiles** (`--profile tools`), not in the default path.
2. **Healthcheck-gated startup**: every service defines `healthcheck`; dependents use `depends_on: { db: { condition: service_healthy } }` — the backend never races the database.
3. **Config via environment**: `env_file: .env` + variable interpolation; `.env` is gitignored, **`.env.example` is maintained and complete** (every variable, dummy values, one comment each). No real secret ever appears in a compose file.
4. **Volumes**: named volume for postgres data; bind mounts only for dev-mode source reload. `docker compose down -v` must be a safe, documented reset.
5. **Networks**: default network, services address each other by service name (`jdbc:postgresql://db:5432/...`); only the frontend (and optionally backend in dev) publish host ports.
6. **Parity across environments**: dev/test/prod-like differ via env values and override files (`compose.override.yaml` for dev conveniences), never via divergent service definitions.
7. Resource limits on prod-like profiles (`mem_limit`) so local behavior approximates the deployment target.

## Examples

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 5s
      timeout: 3s
      retries: 10

  backend:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME}
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      APP_JWT_SECRET: ${JWT_SECRET}
    depends_on:
      db: { condition: service_healthy }
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 10s
      retries: 6

  frontend:
    build: ./frontend
    ports: ["${APP_PORT:-80}:80"]
    depends_on:
      backend: { condition: service_healthy }

volumes:
  pgdata:
```

## Best Practices

- Test the onboarding path regularly: clean clone → copy `.env.example` to `.env` → `docker compose up` → working store (this is a devops success criterion).
- `compose.override.yaml` (auto-merged) holds dev-only conveniences: published DB port, source bind mounts — the base file stays prod-shaped.
- Version-pin everything the same way CI and prod do; drift between compose and pipeline images is a classic "works locally".
- Keep service names stable — they're hostnames baked into config everywhere.

## Common Mistakes

- `depends_on` without health conditions (starts ≠ ready; backend crash-loops on cold db).
- Committing `.env`, or letting `.env.example` rot behind the real variable set.
- Publishing the database port in the base file (dev convenience becomes a prod-like hole).
- Duplicate compose files per env drifting apart — overrides, not copies.
- Resetting stuck local state by deleting random volumes instead of the documented `down -v` + reseed path.

## References

- docs.docker.com/compose (profiles, healthchecks, merge/override)
- See skills `docker`, `nginx`, `github-actions`

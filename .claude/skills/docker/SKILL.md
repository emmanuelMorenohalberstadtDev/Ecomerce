---
name: docker
description: Container images for the Spring Boot backend and Angular frontend — multi-stage builds, minimal secure runtimes, layer caching, and image hygiene.
---

# Docker

## Purpose

Ship small, reproducible, non-root images where the build environment never leaks into the runtime.

## When to Use

Writing/reviewing Dockerfiles, optimizing build time or image size, hardening runtimes.

## Rules

1. **Multi-stage always**: build stage (JDK 21 / node) → runtime stage (JRE / nginx). Build tools, source, and caches never reach the final image.
2. **Pinned bases, digest-preferred**: `eclipse-temurin:21-jre-alpine`, `node:22-alpine`, `nginx:1.27-alpine` — never `latest` (global devops constraint).
3. **Non-root runtime**: create and switch to an unprivileged user in the final stage; read-only filesystem where feasible.
4. **Layer order = change frequency**: dependency manifests copied and resolved *before* source (`pom.xml` → `dependency:go-offline` → `COPY src`), so code edits don't re-download the world.
5. **Layered Spring Boot jars**: extract with layertools/`jarmode` so dependencies, snapshots, and application classes are separate layers — app-only changes push kilobytes, not the fat jar.
6. **No secrets at any stage**: no ARG/ENV tokens baked in; runtime config via environment (see skill `docker-compose`). `.dockerignore` excludes `.git`, `node_modules`, local env files.
7. **HEALTHCHECK** (or compose-level healthcheck) on every service image; containers must be able to say they're ready.
8. One process per container; JVM memory flags container-aware (`-XX:MaxRAMPercentage`, not fixed `-Xmx` guesses).

## Examples

```dockerfile
# backend — build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests && \
    java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# backend — runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
```

```dockerfile
# frontend — build then static serve
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration=production

FROM nginx:1.27-alpine
COPY --from=build /app/dist/*/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

## Best Practices

- `npm ci` (never `npm install`) and lockfile-keyed cache in CI (see skill `github-actions`).
- Scan images (Trivy or equivalent) in the pipeline; treat critical CVEs in base images as build failures per security-engineer's policy.
- Label images with git SHA + build metadata (`org.opencontainers.image.*`) for traceability.
- Rebuild-from-scratch tested regularly: `docker build` with `--no-cache` shouldn't surprise anyone.

## Common Mistakes

- Single-stage images shipping Maven, source, and a JDK to production.
- `COPY . .` before dependency resolution — every commit invalidates the dependency layer.
- Alpine glibc surprises for native deps — verify or use `-slim` debian variants deliberately.
- `EXPOSE` treated as security (it's documentation); real exposure decided in compose/nginx.
- Fixing image bloat by deleting files in a *later* layer (layers are additive — clean within the same RUN or rely on multi-stage).

## References

- docs.docker.com/build (multi-stage, cache); Spring Boot container images guide
- See skills `docker-compose`, `github-actions`, `nginx`

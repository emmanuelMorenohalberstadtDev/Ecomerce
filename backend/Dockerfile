# ── Stage 1: build ───────────────────────────────────────────────────────────
# Maven + JDK 21. Build tools and source never reach the runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# 1. Resolve dependencies first — pom.xml changes are rare, source changes are frequent.
#    This layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

# 2. Copy source and build the fat jar.
COPY src ./src
RUN mvn -q package -DskipTests -B

# 3. Extract layered jar — separates deps / spring-boot-loader / snapshots / app classes.
#    An app-only change pushes kilobytes instead of the entire fat jar.
RUN java -Djarmode=layertools -jar target/ecommerce-api-*.jar extract --destination extracted

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
# JRE 21 only — no JDK, no Maven, no source.
FROM eclipse-temurin:21-jre-alpine

# OCI labels for traceability (git SHA and build time injected by CI).
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
LABEL org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      org.opencontainers.image.source="https://github.com/your-org/ecommerce" \
      org.opencontainers.image.title="ecommerce-api"

# Non-root user — containers must not run as root.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copy layered jar content in order of change frequency (slowest-changing first).
COPY --from=build --chown=app:app /app/extracted/dependencies/          ./
COPY --from=build --chown=app:app /app/extracted/spring-boot-loader/    ./
COPY --from=build --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /app/extracted/application/           ./

USER app

# Container-aware heap: JVM reads cgroup limits, not host RAM.
# MaxRAMPercentage=75 leaves headroom for metaspace and off-heap buffers.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]

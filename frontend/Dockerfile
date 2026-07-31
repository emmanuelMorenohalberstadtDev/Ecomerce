# ── Stage 1: build ───────────────────────────────────────────────────────────
# node:22-alpine — pinned minor series, never 'latest'.
FROM node:22-alpine AS build

WORKDIR /app

# 1. Resolve dependencies first — package.json changes are rare, source changes frequent.
#    npm ci (never npm install) for reproducible, lockfile-exact installs.
COPY package.json package-lock.json ./
RUN npm ci

# 2. Copy source and build for production.
COPY . .
RUN npm run build:prod

# ── Stage 2: static serving ───────────────────────────────────────────────────
# nginx:1.27-alpine — pinned exact version. No node, no source, no npm.
FROM nginx:1.27-alpine

# OCI labels for traceability (git SHA and build time injected by CI).
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
LABEL org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      org.opencontainers.image.source="https://github.com/your-org/ecommerce" \
      org.opencontainers.image.title="ecommerce-frontend"

# Angular CLI outputs to dist/<project>/browser for application files.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# nginx config is maintained in docker/nginx/nginx.conf (repo root).
# In the compose stack, it is bind-mounted so changes don't require a rebuild.
# For a self-contained image (CI artifacts, prod push), copy it in here:
COPY --from=build /app/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

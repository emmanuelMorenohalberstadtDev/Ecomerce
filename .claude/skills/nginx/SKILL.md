---
name: nginx
description: nginx as SPA server and API reverse proxy — routing, compression, caching, security headers, and body-size/timeout tuning for the ecommerce.
---

# nginx

## Purpose

One entry point that serves the Angular build efficiently, proxies `/api` to the backend cleanly, and applies the transport-level security posture security-engineer defines.

## When to Use

Configuring the frontend container's nginx, adding headers, tuning caching/compression, debugging routing or proxy behavior.

## Rules

1. **SPA fallback**: `try_files $uri $uri/ /index.html;` — deep links (`/products/42`) must load the app, not 404. But `index.html` itself is **never cached** (`no-cache`), while hashed build assets get `immutable, max-age=1y`.
2. **API proxy**: `location /api/ { proxy_pass http://backend:8080; }` with forwarded headers (`Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`) — the backend must see real client info for logging and rate decisions.
3. **Security headers** (checklist owned by security-engineer, applied here):
   `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, a project-defined `Content-Security-Policy`, and HSTS when TLS terminates here. `server_tokens off;` always.
4. **Compression**: gzip (and brotli if the image provides it) for text types, `gzip_min_length` sane; never compress already-compressed media.
5. **Limits protect the backend**: `client_max_body_size` matched to real upload needs (small, explicit); proxy timeouts aligned with the API's latency budget — a 60s default timeout hides a broken endpoint.
6. **Exact-match locations for control files**: health endpoints, `robots.txt` — before the SPA catch-all.
7. Config lives in the repo (`nginx.conf` copied into the image, see skill `docker`) — no hand-edited containers.

## Examples

```nginx
server {
    listen 80;
    server_tokens off;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 2m;
        proxy_read_timeout 15s;
    }

    location ~* \.(?:js|css|woff2|png|jpg|webp|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    location / {
        add_header Cache-Control "no-cache";
        try_files $uri $uri/ /index.html;
    }
}
```

## Best Practices

- Test the cache split after every deploy story change: a stale `index.html` pointing at deleted hashed bundles is the classic broken-after-deploy.
- Rate limiting (`limit_req`) on auth endpoints per security-engineer's design — cheap brute-force mitigation at the edge.
- Log format including request time + upstream time so performance-engineer can attribute latency (edge vs backend).
- Keep CSP strict and evolve it with the app (violations in report-only first when tightening).

## Common Mistakes

- `try_files` catch-all swallowing missing asset 404s into `index.html` (JS parse errors from HTML responses).
- Caching `index.html` for a year — users trapped on the old app until hard refresh.
- Duplicated `add_header` scoping surprises: headers set at server level vanish inside locations that add their own (repeat them or use `always` deliberately).
- Proxying without `X-Forwarded-*`, then "all logins come from 172.18.0.1".
- CORS "fixed" in nginx *and* Spring, doubling headers and breaking preflights — CORS is owned by the backend config (skill `spring-security`).

## References

- nginx.org docs; OWASP Secure Headers Project
- See skills `docker`, `docker-compose`, `secure-coding`, `spring-security`

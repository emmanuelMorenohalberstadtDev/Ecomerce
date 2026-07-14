---
name: performance
description: Frontend performance for Angular — Core Web Vitals targets, lazy loading and @defer, bundle discipline, image strategy, and change-detection efficiency.
---

# Performance (Frontend)

## Purpose

Keep the store fast where speed converts: LCP ≤ 2.5s, INP ≤ 200ms, CLS ≤ 0.1 on mid-range mobile — budgets owned by performance-engineer, executed here.

## When to Use

Building any route, adding dependencies, loading images, diagnosing slow interactions or bundle growth.

## Rules

1. **Route-level code splitting is mandatory**: every feature lazy-loads; the initial bundle carries only shell + first route. New npm dependencies need architect approval *and* a bundle-size note in the PR.
2. **`@defer` below the fold**: reviews, recommendations, footers-of-content load `on viewport`/`on idle` with sized `@placeholder` (no CLS).
3. **Images are the LCP**: `NgOptimizedImage` everywhere; `priority` on the LCP image only; explicit dimensions; modern formats server-side; lazy for everything below fold.
4. **OnPush + signals everywhere** (baseline from skill `angular`): change detection cost stays proportional to what changed. `@for` always keyed with `track`.
5. **No layout shift by design**: skeletons and placeholders reserve exact space; fonts with `font-display: swap` + metric fallbacks; never inject banners above content post-load.
6. **INP discipline**: handlers do minimal sync work; heavy computation moves to `computed` (cached), idle callbacks, or a worker; optimistic UI for cart mutations (see design spec) with rollback.
7. **Network shape**: paginate everything; request only fields the view needs (align with API contract); debounce search (see skill `rxjs`); cache immutable catalog data with HTTP caching headers (coordinate with backend).
8. Measure before and after: Lighthouse (mobile, throttled) + bundle analyzer evidence in the PR for perf-flagged changes.

## Examples

```html
@defer (on viewport) {
  <app-product-reviews [productId]="id()" />
} @placeholder {
  <div class="h-64 animate-pulse rounded-lg bg-surface-elevated"></div>
}
```

```html
<img ngSrc="{{ hero.imageUrl }}" width="800" height="800" priority
     sizes="(max-width: 768px) 100vw, 50vw" alt="{{ hero.name }}" />
```

## Best Practices

- Set budgets in `angular.json` (`budgets`: initial bundle warning/error) so CI fails on regressions mechanically.
- Preload the *next likely* route (product list → product detail) with router preloading strategies.
- Virtualize only proven-long lists (CDK virtual scroll for 200+ rows); pagination usually beats virtualization in shops.
- Profile with the real thing: 4× CPU throttle + Fast 3G before declaring victory.

## Common Mistakes

- "One more small library" ×10 — bundle death by a thousand cuts; check what tree-shakes.
- `priority` on several images (or none) — exactly one LCP candidate gets it per view.
- Spinner-only loading states (guaranteed CLS + slow perceived load) instead of sized skeletons.
- Functions called in templates doing work every CD cycle.
- Optimizing the desktop Lighthouse score while mobile (the traffic majority) stays red.

## References

- web.dev/vitals; angular.dev deferred loading & image directive guides
- See skills `angular-signals`, `responsive-design`, `rxjs`; performance-engineer owns the budgets

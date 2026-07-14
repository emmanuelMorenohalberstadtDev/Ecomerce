---
name: responsive-design
description: Mobile-first responsive implementation — breakpoint strategy, fluid layouts, container queries, responsive images, and ecommerce layout patterns.
---

# Responsive Design

## Purpose

One codebase that is genuinely usable from 360px phones to wide desktops — mobile-first because most ecommerce traffic and most layout bugs live there.

## When to Use

Implementing any view, defining breakpoints, choosing grid strategies, handling images and touch ergonomics.

## Rules

1. **Mobile-first always**: base styles target the smallest supported width (360–375px); larger breakpoints only *add* (`md:`, `lg:`). If the desktop design arrives first, derive mobile before coding.
2. **Breakpoints are the design system's** (e.g., `sm 640 / md 768 / lg 1024 / xl 1280`) — content decides which to use; never invent per-component pixel breakpoints.
3. **Fluid by default**: `%`/`fr`/`minmax()`/`clamp()` for widths and type scale; fixed pixel widths only for icons and strict design tokens. No horizontal scroll at any width ≥ 360px — ever.
4. **Container queries for components, media queries for layout**: a product card adapts to its container (grid cell vs carousel slot vs cart row), not the viewport.
5. **Responsive images mandatory**: `NgOptimizedImage` with `sizes`/`srcset` for product images; explicit `width`/`height` (or aspect-ratio boxes) to eliminate CLS.
6. **Touch ergonomics on mobile**: targets ≥ 44px, primary actions thumb-reachable (sticky add-to-cart/checkout bars), no hover-dependent functionality — hover enhances, never gates.
7. Test at minimum: 360, 768, 1024, 1440 — plus one in-between width (e.g., 900px), where grid math usually breaks.

## Examples

```html
<!-- product grid: fluid, no per-breakpoint column micromanagement -->
<ul class="grid gap-4" style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));">
```

```html
<!-- container-query card -->
<div class="@container">
  <article class="flex flex-col @md:flex-row @md:items-center @md:gap-6">
```

```html
<img ngSrc="{{ p.imageUrl }}" width="600" height="600"
     sizes="(max-width: 768px) 50vw, 25vw" [priority]="aboveFold()" alt="{{ p.name }}" />
```

## Best Practices

- Sticky mobile CTA bars for add-to-cart (product page) and checkout summary — the highest-converting responsive pattern in ecommerce.
- Tables (order history) become stacked cards below `md` — never a squeezed 6-column table on a phone.
- `clamp()` for type/space that scales smoothly (`font-size: clamp(1.5rem, 4vw, 2.25rem)` for page titles).
- Check with real content: longest product names, 2-line prices with discounts, RTL-safe spacing (logical properties).

## Common Mistakes

- Desktop-first then "hiding things" on mobile — mobile users need the *same* capabilities, re-laid-out.
- Breakpoint-hopping bugs: testing only at the exact breakpoint widths (bugs live between them).
- `100vw` sections causing scrollbar-width horizontal overflow.
- Hiding content with `display:none` per viewport while still downloading it (double-rendering heavy components per breakpoint).
- Fixed-height heroes/cards that clip translated or long text.

## References

- web.dev/learn/design; MDN container queries
- See skills `tailwind`, `performance`, `ecommerce-design-system`, `accessibility`

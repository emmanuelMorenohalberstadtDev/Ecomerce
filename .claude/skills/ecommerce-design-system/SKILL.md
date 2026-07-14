---
name: ecommerce-design-system
description: The store's design system — token architecture, core commerce components (product card, price, cart, checkout), state completeness, and consistency rules.
---

# Ecommerce Design System

## Purpose

One visual and interaction language for the whole store: every screen assembled from specified, reusable, accessible components bound to tokens — zero improvised UI.

## When to Use

Defining/extending tokens or components (ui-ux-designer), implementing them (frontend-lead), reviewing UI consistency.

## Rules

1. **Token architecture, two layers**:
   - *Primitives*: raw scales (`gray-100…900`, `space-1…12`, type scale) — never used directly in specs or templates.
   - *Semantic*: what things mean — `surface`, `surface-elevated`, `on-surface`, `on-surface-muted`, `brand`, `on-brand`, `danger`, `success`, `outline`, `focus-ring`; `space-inline-*`, `space-stack-*`; `radius-sm/md/full`. Specs and code reference semantics only (see skill `tailwind`).
2. **Commerce-critical tokens are explicit**: `price`, `price-discounted`, `price-original` (struck), `badge-sale`, `badge-new`, `stock-ok`, `stock-low`, `stock-out` — pricing display is never restyled ad hoc.
3. **Core component set** (spec before any feature builds a variant): Button (intent × size), Input + Field (label/error/hint), Price, ProductCard, QuantityStepper, Badge, Rating, CartLineItem, OrderSummary, EmptyState, Skeleton, Dialog, Drawer, Toast, Breadcrumbs, Pagination.
4. **Every component spec defines all states**: default, hover, focus-visible, active, disabled, loading, error — plus content edge cases (long names, missing image, 2-line prices). An unspecified state is a spec bug (ui-ux-designer rule).
5. **Price display rules are law**: original price struck + accessible (`<del>` with sr-only "original price"), discount always computed server-side, currency formatting per locale in ONE shared Price component — never hand-formatted per feature.
6. **New component procedure**: proposed to ui-ux-designer with the ≥ 2 uses (or strong single case) justification → specified → built in `shared/` → registered in the components-status table. Feature folders never ship lookalike duplicates.

## Examples

Product card spec skeleton (what a complete spec includes):

```
ProductCard
- Anatomy: image (1:1, NgOptimizedImage), badges (top-left, max 2), name (2-line clamp),
  Price, Rating (optional), AddToCart button / OutOfStock state
- Container-driven layout: grid cell (vertical) / carousel (fixed w) / list (horizontal @md)
- States: default · hover (elevation token + image zoom per motion spec) · focus-visible
  (ring token on whole card link) · loading (Skeleton variant) · out-of-stock
  (image desaturated, stock-out label, CTA replaced by "Notify me")
- Content edges: 60-char names, no image (placeholder token), price with/without discount
- A11y: one link (name = accessible name), button not nested inside link, alt from product
```

## Best Practices

- Components-status table (draft/specified/implemented/verified) kept current — it's the designer↔frontend contract surface.
- Audit quarterly for drift: screenshot key flows, diff against specs; ad-hoc styles found in review get folded back into the system.
- Dark mode = token remapping only; components stay ignorant (see skill `tailwind`).
- Specs live next to the system doc, versioned in the repo — not in a tool nobody checks.

## Common Mistakes

- Two subtly different product cards (home vs category) because features didn't talk — one component, container queries.
- Price formatting re-implemented in four components, disagreeing on decimals.
- Semantic tokens skipped "temporarily" (`text-gray-500` shipped) — drift starts there.
- Specifying only the resting state and letting hover/focus/loading be improvised in code.
- A "design system" that is a Figma file no one syncs — the repo doc is the source of truth for this project.

## References

- designsystems.com guides; Material 3 / Polaris as reference architectures (not to copy visually)
- See skills `tailwind`, `ecommerce-accessibility`, `ecommerce-animation`, `responsive-design`

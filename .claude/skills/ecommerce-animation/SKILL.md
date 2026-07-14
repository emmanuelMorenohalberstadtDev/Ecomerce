---
name: ecommerce-animation
description: Motion design for the store — the add-to-cart moment, transitions that aid shopping, motion tokens, and the purchase-flow calm rule.
---

# Ecommerce Animation

## Purpose

Motion that sells: confirm actions instantly, preserve spatial context while browsing, and get out of the way completely during payment.

## When to Use

Specifying any store animation (motion-designer), implementing specs (frontend-lead), reviewing motion consistency.

## Rules

1. **Motion tokens, not ad-hoc values** (rows in the design system):
   - Durations: `motion-fast: 120ms` (feedback), `motion-base: 240ms` (transitions), `motion-slow: 400ms` (choreography cap).
   - Easings: `ease-out-standard` (entrances), `ease-in-accel` (exits), `ease-in-out-move` (position changes).
2. **The add-to-cart moment is the flagship**: button press feedback ≤ 100ms (scale/opacity) → item flies/fades toward cart icon OR badge pulse+increment → toast/mini-cart confirmation. Total ≤ 500ms, fully interruptible, badge update is the source of truth (animation is garnish on state, never the state).
3. **Purchase-flow calm rule**: checkout and payment screens get functional motion only (field error shakes ≤ 1, step transitions) — no decorative movement where money is entered. Trust reads as stillness.
4. **Spatial continuity**: product card → detail uses shared-element feel (image position/scale transition); drawers slide from their anchor; dialogs scale from trigger area — motion explains where things came from.
5. **Every spec includes**: trigger, animated properties (`transform`/`opacity` only — skill `performance` budget), token duration/easing, interruption behavior, and the `prefers-reduced-motion` fallback (instant state change; badge increments without pulse; toasts appear without slide).
6. **List choreography capped**: stagger 20–40ms, max ~6 staggered items, total sequence ≤ `motion-slow` regardless of count — page 2 of a product grid must not take longer to settle than page 1.
7. Loading motion: skeletons pulse subtly (opacity), spinners only for actions < 1s expected; over that, progress or optimistic UI per design spec.

## Examples

Add-to-cart spec (the format every animation spec follows):

```
AddToCart feedback
- Trigger: click/tap "Add" (also Enter/Space via button semantics)
- Sequence:
  1. Button: scale 0.97, motion-fast, ease-out-standard; label → check icon 200ms
  2. Cart badge: count increments; pulse scale 1→1.2→1, motion-base
  3. Mini-cart toast: translateY(8px)+fade in, motion-base; auto-dismiss 4s (pause on hover/focus)
- Interruption: re-click restarts from step 2 (no queue); navigation cancels all
- Reduced motion: no scale/pulse/slide — icon swap + badge increment + toast fade only
- Perf: transform/opacity only; no layout properties
```

## Best Practices

- Animate state, don't fake it: the badge count comes from the cart store signal; the pulse merely draws the eye to a real change.
- Prototype timing at 0.25× speed to judge easing, ship at full speed — curves hide at 60fps.
- Verify on a throttled mid-range mobile profile (with performance-engineer) before sign-off, not just a dev machine.
- Reuse interaction specs: QuantityStepper feels identical in cart, product page, and mini-cart.

## Common Mistakes

- Celebration confetti on add-to-cart (save maximal feedback for order-placed, once).
- Motion specs living only in the implementer's head — every animation traceable to a spec (motion-designer rule).
- Blocking input during transitions (route changes that eat the user's next tap).
- Skeleton-to-content jumps because the skeleton didn't match final layout (CLS — see skill `performance`).
- Different drawer physics for cart vs filters — one drawer spec, period.

## References

- Material Motion guidelines (durations/easing rationale); web.dev animations guide
- See skills `ecommerce-design-system`, `performance`, `accessibility`

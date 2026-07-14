---
name: accessibility
description: WCAG 2.2 AA implementation in Angular — semantics, keyboard support, focus management, ARIA discipline, and forms/announcements patterns.
---

# Accessibility

## Purpose

Make the app operable by keyboard, understandable by screen readers, and readable by everyone — as engineering requirements, not polish.

## When to Use

Building any interactive component, forms, dialogs, dynamic content; reviewing frontend PRs. (Ecommerce-specific flows: skill `ecommerce-accessibility`.)

## Rules

1. **Semantic HTML first**: `button` for actions, `a` for navigation, `nav/main/header/footer` landmarks, one `h1` per page with ordered heading levels. A clickable `div` is a review blocker.
2. **Keyboard complete**: every interaction reachable and operable via keyboard; visible focus (`focus-visible` ring from the design system); logical tab order; no positive `tabindex`.
3. **Focus management on context change**: route change moves focus to the new page's `h1`/main; dialog open traps focus and returns it on close; deleting an item moves focus to a sensible neighbor.
4. **ARIA is a last resort**: first the right element, then ARIA to fill genuine gaps (`aria-expanded`, `aria-current="page"`, `aria-live`). No ARIA is better than wrong ARIA.
5. **Forms**: every input has a programmatic `label`; errors linked via `aria-describedby` + `aria-invalid`; error summaries focusable. Placeholder is never the label.
6. **Dynamic updates announced**: async results, cart changes, toasts → `aria-live="polite"` region (`assertive` only for errors requiring immediate attention).
7. **Contrast**: text ≥ 4.5:1 (3:1 for large text), UI components/focus indicators ≥ 3:1 — token pairs in the design system are pre-validated; new combinations require a check.
8. **Media & motion**: images have meaningful `alt` (or `alt=""` if decorative); respect `prefers-reduced-motion` (see skill `ecommerce-animation`).
9. Touch targets ≥ 44×44px (WCAG 2.2); `title`/`aria-label` on icon-only buttons.

## Examples

```html
<!-- quantity stepper: real buttons, announced state -->
<div role="group" aria-labelledby="qty-label">
  <span id="qty-label" class="sr-only">Quantity for {{ product().name }}</span>
  <button type="button" (click)="decrement()" [disabled]="qty() <= 1" aria-label="Decrease quantity">−</button>
  <output aria-live="polite">{{ qty() }}</output>
  <button type="button" (click)="increment()" aria-label="Increase quantity">+</button>
</div>
```

```typescript
// route change focus (core/ helper)
router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed())
  .subscribe(() => document.querySelector<HTMLElement>('main h1')?.focus());
```

## Best Practices

- Test with keyboard only, then with a screen reader (NVDA/VoiceOver) on the money path each release.
- Automate the floor: eslint a11y rules + axe checks in CI catch ~40%; manual passes catch the rest.
- Reuse accessible primitives (dialog, menu, tabs) from the shared library — hand-rolling ARIA widgets per feature breeds bugs.
- Write a11y notes into design specs (ui-ux-designer) so behavior is specified, not improvised.

## Common Mistakes

- `div (click)` + cursor-pointer masquerading as a button (no keyboard, no semantics, no focus).
- `aria-label` overriding visible text with something different (breaks voice control).
- Focus outline removed globally for aesthetics.
- Live regions added *after* the announcement is needed (must exist in DOM first).
- Disabling zoom (`user-scalable=no`) or conveying state by color alone.

## References

- WCAG 2.2 (w3.org/TR/WCAG22), ARIA Authoring Practices Guide (w3.org/WAI/ARIA/apg)
- See skills `ecommerce-accessibility`, `angular`, `tailwind`

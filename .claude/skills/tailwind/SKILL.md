---
name: tailwind
description: Tailwind CSS in the Angular design system — token-driven theming, class organization, component extraction rules, and dark-mode/responsive variants.
---

# Tailwind

## Purpose

Style with utilities bound to the design system's tokens, so every color/space/size in the UI traces to a token ui-ux-designer defined — no magic values.

## When to Use

Styling any component, configuring the theme, deciding utility-vs-extraction, reviewing template class usage.

## Rules

1. **Tokens first**: the Tailwind theme maps 1:1 to design-system tokens (`@theme` CSS variables in v4). Arbitrary values (`p-[13px]`, `text-[#3b82f6]`) are review blockers — if a value is missing, it goes into the theme via ui-ux-designer, not inline.
2. **Semantic color names** in the theme (`surface`, `surface-elevated`, `on-surface`, `brand`, `danger`) — never raw palette references (`blue-500`) in templates.
3. **Repetition rule**: the same utility cluster appearing 3+ times becomes an Angular shared component (preferred) — not `@apply` soup. `@apply` is allowed only inside genuinely global primitives (base button/badge), sparingly.
4. **Class order convention** (readability, enforced by prettier-plugin-tailwindcss): layout → spacing → sizing → typography → color → border → effects → states/variants.
5. **Responsive is mobile-first**: base classes are the 375px design; `md:`/`lg:` add up. Desktop-first `max-*` variants need justification.
6. **States styled explicitly**: `hover:`, `focus-visible:` (never bare `focus:` for keyboard rings), `disabled:`, `aria-*`/`data-*` variants for component state (`aria-expanded:rotate-180`).
7. Variant logic in TypeScript, not template ternaries: a `class` computed from typed props (buttons: intent × size).

## Examples

```typescript
// variant map: typed, testable, single source for button styles
const BUTTON: Record<Intent, string> = {
  primary: 'bg-brand text-on-brand hover:bg-brand-hover focus-visible:ring-2 focus-visible:ring-brand',
  secondary: 'border border-outline bg-surface text-on-surface hover:bg-surface-elevated',
  danger: 'bg-danger text-on-danger hover:bg-danger-hover',
};
readonly classes = computed(() =>
  `inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium
   transition-colors disabled:pointer-events-none disabled:opacity-50 ${BUTTON[this.intent()]}`);
```

```html
<!-- product grid: mobile-first, token-driven -->
<ul class="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4 lg:gap-6">
```

## Best Practices

- Configure `content` paths correctly so purging keeps the bundle lean; audit CSS size with performance budgets.
- Dark mode via the `dark:` variant on semantic tokens only — components shouldn't know dark mode exists beyond token flips.
- Container queries (`@container`) for components that adapt to their slot, not the viewport (product card in grid vs carousel).
- Keep template class strings formatted (one concern per line in long lists) — unreadable class soup hides bugs.

## Common Mistakes

- Arbitrary values sprinkled to "match the design" — the design *is* tokens; fix the theme.
- `@apply`-ing everything back into CSS files (recreating the framework Tailwind replaced).
- Copy-pasting a card's 30 classes into 6 components, then fixing 5 of them.
- `focus:outline-none` without a `focus-visible` replacement (a11y blocker, see skill `accessibility`).
- Styling states via class toggling in TS when an `aria-`/`data-` variant is cleaner and keeps a11y state honest.

## References

- tailwindcss.com v4 docs; prettier-plugin-tailwindcss
- See skills `ecommerce-design-system`, `responsive-design`, `accessibility`

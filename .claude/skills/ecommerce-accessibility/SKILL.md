---
name: ecommerce-accessibility
description: Accessibility for commerce flows specifically — product discovery, cart announcements, checkout forms, price semantics, and payment accessibility.
---

# Ecommerce Accessibility

## Purpose

Every shopper can find, evaluate, and buy — screen reader users complete checkout unassisted, keyboard users never get trapped, and prices are never ambiguous. (General a11y baseline: skill `accessibility`; this skill covers commerce-specific patterns.)

## When to Use

Designing/implementing/reviewing catalog, cart, and checkout experiences.

## Rules

1. **Product cards**: one link whose accessible name is the product name (not "View details" ×20); the add-to-cart button is a sibling, never nested inside the link; image `alt` describes the product, not "product image".
2. **Prices are semantic**: discounted price pattern reads correctly — `<del>` original with sr-only "original price", current price with sr-only "current price"; discount badges have text equivalents ("30% off", not color alone). Currency announced, not assumed.
3. **Cart changes are announced**: add/remove/quantity updates fire a polite live region ("Product X added to cart, 3 items, total $4,500") — the badge alone is invisible to screen readers. Quantity steppers per the group pattern in skill `accessibility`.
4. **Filters and sorting**: applied filters announced with result counts ("42 results for Shoes under $10,000"); filter drawers follow the dialog focus pattern; sort is a real `select` or a proper listbox — not styled divs.
5. **Checkout forms are the exam**:
   - Full autocomplete tokens (`autocomplete="name|street-address|postal-code|cc-number|…"`) — motor-impaired and cognitive-load wins, and WCAG 1.3.5.
   - One-thing-per-step or clearly grouped `fieldset/legend`; progress announced ("Step 2 of 3, Shipping").
   - Errors: focus moves to a summary linking each field; fields get `aria-invalid` + `aria-describedby`; messages say how to fix, never just "invalid".
   - No time limits on session/cart during checkout without warning + extension (WCAG 2.2.1).
6. **Stock and availability by text**: "Only 2 left" / "Out of stock" as text; disabled add-to-cart buttons still explain why (visible text or described-by, not a silent disabled state).
7. **Order confirmation**: focus lands on the confirmation heading; the order number is plain selectable text.

## Examples

```html
<!-- discounted price, unambiguous to screen readers -->
<p class="flex items-baseline gap-2">
  <span class="text-price-discounted font-semibold">
    <span class="sr-only">Current price</span>$6.999
  </span>
  <del class="text-price-original text-sm">
    <span class="sr-only">Original price</span>$9.999
  </del>
  <span class="rounded bg-badge-sale px-1 text-xs">30% off</span>
</p>
```

```typescript
// cart live region (one, global, in the layout shell)
readonly announcement = signal('');
add(item: CartItem) {
  this.cart.addItem(item);
  this.announcement.set(
    `${item.name} added to cart. ${this.cart.totalQuantity()} items, total ${formatPrice(this.cart.subtotal())}.`);
}
// template: <p aria-live="polite" class="sr-only">{{ announcement() }}</p>
```

## Best Practices

- Screen-reader walkthrough of the full money path (search → buy) each release — automated tools can't judge whether the checkout *narrative* makes sense.
- Test checkout with autofill actually enabled — broken autocomplete tokens surface immediately.
- Keep one live-region utility in the shell; scattered per-component live regions double-announce.
- Design specs carry these requirements (ui-ux-designer includes them; frontend implements; reviewer checks).

## Common Mistakes

- Twenty "Add to cart" buttons with identical accessible names (add the product: `aria-label="Add {{name}} to cart"`).
- Struck-through price with no semantics — screen readers announce two prices with no distinction, users buy at the wrong expectation.
- Toast-only feedback (visual, 4s, unannounced) as the sole confirmation of cart changes.
- Card grids where the whole card is a click-handler div: no link semantics, no keyboard, no "open in new tab".
- Coupon/promo inputs that validate on blur and steal focus mid-typing.

## References

- WCAG 2.2 (1.3.5, 2.2.1, 3.3.x); W3C WAI e-commerce patterns; APG dialog/listbox patterns
- See skills `accessibility`, `ecommerce-design-system`, `validation`

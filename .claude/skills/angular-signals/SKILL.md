---
name: angular-signals
description: Signals-based state management — signal/computed/effect/linkedSignal, resource APIs for async data, signal stores per feature, and signals-vs-RxJS boundaries.
---

# Angular Signals

## Purpose

Manage all UI state with signals: synchronous, glitch-free derivation that pairs with OnPush for minimal re-rendering.

## When to Use

Any component/service state, derived values, async data loading, deciding signal vs observable.

## Rules

1. **State lives in `signal()`, derivation in `computed()`** — never store what can be computed (cart total is `computed`, not a second signal to keep in sync).
2. **`effect()` is for the outside world only** (localStorage sync, analytics, imperative DOM APIs). An effect that sets another signal is a design error — use `computed` or `linkedSignal`.
3. **Async data via `resource`/`httpResource`** (or `toSignal` over an observable): loading/error/value states come from the API, not hand-rolled `isLoading` booleans.
4. **Feature state in a signal store service**: private writable signals, public `asReadonly()`/computed views, mutations through named methods. Components never mutate shared state directly.
5. **Updates are pure**: `update(fn)` with immutable patterns for objects/arrays — mutation breaks equality-based propagation.
6. **Boundary with RxJS**: streams of *events over time* (search debounce, route params, websockets) stay observables and convert at the edge with `toSignal`; state never round-trips signal → observable → signal.
7. Component inputs: `input()`/`input.required()`; two-way with `model()`.

## Examples

```typescript
@Injectable() // provided at the cart feature route
export class CartStore {
  private readonly _items = signal<CartItem[]>([]);
  readonly items = this._items.asReadonly();
  readonly totalQuantity = computed(() => this._items().reduce((s, i) => s + i.quantity, 0));
  readonly subtotal = computed(() => this._items().reduce((s, i) => s + i.unitPrice * i.quantity, 0));
  readonly isEmpty = computed(() => this._items().length === 0);

  addItem(item: CartItem) {
    this._items.update(items => {
      const existing = items.find(i => i.productId === item.productId);
      return existing
        ? items.map(i => i === existing ? { ...i, quantity: i.quantity + item.quantity } : i)
        : [...items, item];
    });
  }
}
```

```typescript
// async with built-in states
readonly productId = input.required<string>();
readonly product = httpResource<Product>(() => `/api/v1/products/${this.productId()}`);
// template: @if (product.isLoading()) ... @else if (product.error()) ... @else { use product.value() }
```

## Best Practices

- Name computed signals as facts (`isEmpty`, `canCheckout`), methods as actions (`addItem`).
- Keep stores per feature and provide them at the feature route — global singletons only for truly global state (auth session, cart badge).
- `linkedSignal` for state that resets when a source changes (selected variant resets when product changes).
- `untracked()` inside effects when reading signals that shouldn't retrigger.

## Common Mistakes

- Effects as data pipelines (effect → set signal → effect...) — cascade bugs; derive instead.
- Duplicating server state into multiple signals that drift — one resource, many computeds.
- Calling `set()` on a signal from a template event *and* deriving it elsewhere — pick one owner.
- Storing component `input()` into a local signal copy (inputs already are signals).
- Overusing `model()` two-way binding for what is really an event up + prop down.

## References

- angular.dev/guide/signals, angular.dev resource API docs
- See skills `angular`, `rxjs`, `performance`

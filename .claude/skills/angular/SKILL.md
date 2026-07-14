---
name: angular
description: Angular 21 baseline — standalone components, native control flow, inject(), typed reactive forms, routing/guards/interceptors, and project structure.
---

# Angular

## Purpose

Write modern, strict Angular 21: standalone everything, signals-first, lazy by default, typed end to end.

## When to Use

Creating components/services/routes, structuring features, wiring HTTP and forms — the baseline for all frontend work.

## Rules

1. **Standalone only** — no NgModules. Components declare their own `imports`.
2. **`ChangeDetectionStrategy.OnPush` on every component**, no exceptions; state changes flow through signals.
3. **Native control flow**: `@if`, `@for` (always with `track`), `@switch`, `@defer` — never `*ngIf`/`*ngFor` in new code.
4. **`inject()`** over constructor parameters; `input.required<T>()` / `output<T>()` over decorators.
5. **Strict TypeScript**: no `any`, no non-null assertions to silence the compiler, no `@ts-ignore` without a ticket.
6. **Structure per `docs/conventions.md`**: `core/` (singletons, interceptors, guards), `shared/` (≥ 2 consumers rule), `features/<name>/` (components, services, models, `<name>.routes.ts`), `layout/`.
7. **Routing**: every feature lazy-loaded via `loadChildren`/`loadComponent`; functional guards (`CanActivateFn`); route-level `providers` for feature-scoped services.
8. **HTTP**: one typed service per API resource returning domain models; interceptors (functional) own auth headers and problem-detail error mapping — components never see raw `HttpErrorResponse`.
9. **Forms**: typed reactive forms; validators mirror backend rules (see skill `validation`); errors shown on touched + invalid.

## Examples

```typescript
@Component({
  selector: 'app-product-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (product(); as p) {
      <article>
        <h3>{{ p.name }}</h3>
        <button (click)="addToCart.emit(p.id)" data-testid="add-to-cart">Add</button>
      </article>
    }
  `,
})
export class ProductCardComponent {
  product = input.required<Product>();
  addToCart = output<string>();
}
```

```typescript
export const CATALOG_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./product-list.component').then(m => m.ProductListComponent) },
  { path: ':id', loadComponent: () => import('./product-detail.component').then(m => m.ProductDetailComponent) },
];
```

## Best Practices

- Presentational vs container split: presentational components take `input`s/`output`s and inject nothing stateful.
- `data-testid` on interactive elements (contract with test tooling).
- Models as interfaces mirroring the OpenAPI contract exactly — one `models/` per feature.
- `providedIn: 'root'` for app-wide singletons; route `providers` for feature state.

## Common Mistakes

- Logic in templates (chained ternaries, method calls per CD cycle) — move to `computed()`.
- Subscribing in components for data that belongs in a signal (`toSignal`, `resource`).
- `shared/` as a dumping ground after first use — the second consumer promotes, not the first.
- Missing `track` in `@for` (destroys DOM reuse on list updates).
- Guessing API shapes instead of reading the OpenAPI contract.

## References

- angular.dev (v21 docs)
- See skills `angular-signals`, `rxjs`, `performance`, `accessibility`, `tailwind`

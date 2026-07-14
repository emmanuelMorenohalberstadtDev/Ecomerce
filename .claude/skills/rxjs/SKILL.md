---
name: rxjs
description: RxJS in a signals-first Angular app — where observables still win, the essential operators, subscription hygiene, and conversion at the signal boundary.
---

# RxJS

## Purpose

Use RxJS for what it's uniquely good at — composing asynchronous *event streams* — and keep it out of plain state management, which belongs to signals.

## When to Use

Search-as-you-type, debounced inputs, cancellable request chains, router event reactions, retry/backoff logic. NOT for holding state (see skill `angular-signals`).

## Rules

1. **Decision rule**: is it a *value that changes* (signal) or *events over time needing composition* (observable)? When in doubt, signal.
2. **Convert at the edge**: observable pipelines end in `toSignal()` (or `async` pipe); components consume signals. No manual `.subscribe()` in components except for fire-and-forget actions.
3. **Every manual subscription has a termination story**: `takeUntilDestroyed()`, `take(1)`, or `toSignal`'s auto-cleanup. Unbounded subscriptions are review blockers.
4. **Flattening operators by intent**:
   - `switchMap` — cancel previous (search, typeahead, param-driven loads)
   - `concatMap` — ordered side effects (sequential cart mutations)
   - `exhaustMap` — ignore re-triggers while busy (submit buttons, login)
   - `mergeMap` — parallel independent (rare; justify concurrency)
5. **Error handling inside the pipe**: `catchError` returning a typed fallback per emission — an unhandled error kills the stream silently.
6. No nested subscribes, ever — that's a flattening operator missing.
7. Multicasting: `shareReplay({ bufferSize: 1, refCount: true })` when several consumers need one HTTP result — and prefer a resource/store instead when it's really state.

## Examples

```typescript
// search-as-you-type: the canonical legitimate RxJS use
private readonly query$ = new Subject<string>();
readonly results = toSignal(
  this.query$.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    switchMap(q => q.length < 2
      ? of([])
      : this.api.search(q).pipe(catchError(() => of([])))),
  ),
  { initialValue: [] as Product[] },
);
search(q: string) { this.query$.next(q); }
```

```typescript
// exhaustMap: double-click-proof checkout submit
readonly submit$ = new Subject<void>();
constructor() {
  this.submit$.pipe(
    exhaustMap(() => this.orders.place(this.checkoutForm.getRawValue())),
    takeUntilDestroyed(),
  ).subscribe(result => this.router.navigate(['/orders', result.id]));
}
```

## Best Practices

- Keep pipelines short (≤ 5 operators) and named: extract intermediate observables with meaningful names.
- Type every stream explicitly; `Subject<void>` for triggers.
- Test pipelines with real (subscribed) emissions, not implementation internals; virtual time for debounce.
- `retry({ count: 2, delay: backoff })` only on idempotent GETs — never blind-retry mutations.

## Common Mistakes

- Building a state store out of `BehaviorSubject`s in 2026 — that's the signal store's job.
- `switchMap` on mutations (cancels an in-flight cart update, losing the write) — use `concatMap`/`exhaustMap`.
- `catchError` outside the inner observable, terminating the whole stream after the first error.
- Subscribing in a service constructor to "warm up" data — lazy resources instead.
- Mixing `async` pipe and manual subscription on the same stream in one component.

## References

- rxjs.dev operator decision tree; angular.dev rxjs-interop
- See skills `angular-signals`, `angular`

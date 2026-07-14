---
description: Implement frontend work — Angular 21 (frontend-lead; requests design spec if missing)
argument-hint: "[the frontend task: page, component, integration, form]"
---

Implement the following frontend task: $ARGUMENTS

1. Preconditions check:
   - UI work without a design spec → invoke **ui-ux-designer** first to produce it (states, breakpoints, a11y notes). Never improvise visual decisions.
   - API integration without an OpenAPI contract → stop and route the gap to **backend-lead**; the frontend never guesses endpoints.
2. Invoke the **frontend-lead** agent with the task, design spec, and contract. It must:
   - Apply skills `angular`, `angular-signals`, `tailwind`, `accessibility`, `responsive-design`, `performance` (and `rxjs`, `ecommerce-design-system`, `ecommerce-accessibility` as relevant).
   - Implement all four view states (loading / empty / error / success), OnPush + signals, mobile-first.
   - Implement motion per **motion-designer** specs when they exist, including reduced-motion fallbacks.
3. Report what was implemented and note screenshots (desktop + 375px mobile) required for the PR.

Constraints: no new npm dependencies without **software-architect** approval; design spec deviations are reported back to ui-ux-designer, never silently patched.

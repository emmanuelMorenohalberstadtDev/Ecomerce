---
description: Design UI — visual spec plus motion (ui-ux-designer + motion-designer)
argument-hint: "[the screen/component/flow to design]"
---

Design the UI for: $ARGUMENTS

1. Invoke the **ui-ux-designer** agent: produce the design spec — layout per breakpoint (mobile-first from 375px), design-system tokens/components used (skill `ecommerce-design-system`), all four states (loading/empty/error/success) plus interactive states (hover/focus/active/disabled), and a11y notes (skills `accessibility`, `ecommerce-accessibility`). New components require the ≥ 2 uses justification and get registered in the components-status table.
2. Then invoke the **motion-designer** agent on the finished static spec: animation specs per interaction (trigger, properties — transform/opacity only —, motion tokens, interruption behavior, reduced-motion fallback) per skill `ecommerce-animation`.
3. Deliver both specs together as the implementation-ready package for **frontend-lead**.

Constraints: specs only, no Angular/Tailwind implementation. Motion never redefines layout — layout questions return to ui-ux-designer.

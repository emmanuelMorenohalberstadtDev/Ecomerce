---
description: Design user experience — flows, IA, journey decisions (ui-ux-designer agent)
argument-hint: "[the journey/flow/experience question]"
---

Work the UX for: $ARGUMENTS

Invoke the **ui-ux-designer** agent in UX mode (flows and experience, not pixels). It must deliver:

- **User flow**: steps, decision points, and error/recovery paths for the journey (e.g., guest checkout, returns, search-to-purchase) — with the "why" of each flow decision documented so future changes don't regress conversion logic.
- **Information architecture** when relevant: navigation, categorization, filter/search behavior.
- **Story feedback**: if the underlying user story dictates a screen solution instead of a goal, push back to **product-owner** with the reframed problem.
- Conversion-critical reasoning made explicit: friction points identified, states where users abandon, and what the flow does about them.

Constraints: business rules (what a guest may do, when stock is promised) are **product-owner** decisions — this command surfaces the questions, never answers them unilaterally. Visual specs are `/ui`'s job; this command's output feeds it.

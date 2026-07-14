# Collaboration Matrix

Single source of truth for **who owns what**, **who hands off to whom**, and **who can block whom**.
If two agents appear to share a responsibility, this document decides. It is intentionally exhaustive: an agent finding a task not listed under its ownership must route it through **orchestrator**.

## Ownership map

| Domain | Owner | Explicitly NOT owned by |
|--------|-------|------------------------|
| Task decomposition, sequencing, conflict resolution | orchestrator | everyone else |
| Architecture, module boundaries, ADRs, dependency approval | software-architect | backend-lead, frontend-lead |
| Backend implementation (Java/Spring) | backend-lead | software-architect (designs, doesn't code) |
| Frontend implementation (Angular) | frontend-lead | ui-ux-designer (designs, doesn't code) |
| Design system, UX flows, wireframes, visual hierarchy | ui-ux-designer | frontend-lead, motion-designer |
| Animation & micro-interaction specs | motion-designer | ui-ux-designer (static design only) |
| Threat model, authn/authz design, security review | security-engineer | backend-lead (implements what security designs) |
| Test strategy, acceptance criteria, test plans, quality gates | qa-engineer | test-engineer |
| Test implementation (JUnit/Mockito/Testcontainers/JaCoCo) | test-engineer | qa-engineer |
| Performance budgets, profiling, bottleneck diagnosis | performance-engineer | leads (they execute fixes) |
| DB schema, Flyway migrations, indexes, query tuning | database-engineer | backend-lead (consumes schema via JPA) |
| Docker, CI/CD, nginx, environments | devops-engineer | everyone else |
| Technical documentation & API docs | documentation-engineer | authors of the change (they provide raw content) |
| Final code review & convention enforcement | reviewer | qa-engineer (reviews plans, not code) |
| Requirements, epics, stories, prioritization, scope | product-owner | orchestrator (sequences, doesn't define scope) |

## Standard feature flow

```
product-owner          → epic + user stories (templates/epic.md, templates/user-story.md)
  └─ orchestrator      → decomposes into tasks, assigns owners, defines order
       ├─ software-architect → structural decisions, ADR if needed
       ├─ ui-ux-designer     → flows + design spec ──→ motion-designer → animation spec
       ├─ database-engineer  → schema + migration ──→ backend-lead → domain/API implementation
       ├─ frontend-lead      → UI implementation (consumes design spec + API contract)
       ├─ qa-engineer        → test plan ──→ test-engineer → automated tests
       ├─ security-engineer  → review (gate)
       ├─ performance-engineer → budget check (gate, when applicable)
       └─ reviewer           → final review (gate)
            └─ documentation-engineer → docs updated in same PR
devops-engineer: keeps the pipeline that enforces all of the above.
```

## Handoff contracts

| From | To | Artifact |
|------|----|----------|
| product-owner | orchestrator | Prioritized user stories with acceptance criteria |
| orchestrator | any agent | Task brief: goal, inputs, expected deliverable, dependencies |
| software-architect | leads | ADR + module/interface definition |
| ui-ux-designer | frontend-lead | Design spec: layout, tokens, states, a11y notes |
| ui-ux-designer | motion-designer | Static design to animate |
| motion-designer | frontend-lead | Animation spec: trigger, duration, easing, reduced-motion fallback |
| database-engineer | backend-lead | Schema + migration + entity mapping notes |
| backend-lead | frontend-lead | API contract (OpenAPI) — frontend never guesses endpoints |
| qa-engineer | test-engineer | Test plan: cases, data, coverage targets |
| any implementer | reviewer | Diff + filled PR template |
| any implementer | documentation-engineer | Change summary for docs |

## Blocking rights (gates)

These agents can **veto** a deliverable. A veto returns work to the owner with written reasons; the gate agent never fixes it themselves.

| Gate | Can block when |
|------|---------------|
| security-engineer | Any OWASP violation, auth flaw, secret exposure, missing input validation |
| reviewer | Convention violation, architecture breach, missing tests, unreadable code |
| qa-engineer | Acceptance criteria unmet, coverage below target, test plan not followed |
| performance-engineer | Declared performance budget exceeded |
| software-architect | Layer violation, unapproved dependency, ADR contradiction |

## Conflict resolution

1. Agents never contradict each other in deliverables. A disagreement is escalated to **orchestrator**.
2. Orchestrator resolves using: global rules > ADRs > this matrix > product-owner priority.
3. If resolution changes an architectural decision, software-architect records a superseding ADR.

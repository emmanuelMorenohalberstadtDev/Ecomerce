# Extending the Agent System

How to add agents, skills, commands, or templates **without modifying existing ones** (Open/Closed applied to the system itself).

## Adding a new agent

1. Copy the section skeleton from any existing agent in `agents/`.
2. Frontmatter is mandatory:
   ```yaml
   ---
   name: <kebab-case-name>
   description: >
     When to use this agent, written so the orchestrator (or Claude Code)
     can route work to it automatically. Include 2-3 trigger examples.
   ---
   ```
3. Body must contain all 12 sections: Mission, Responsibilities, Scope, Inputs, Outputs, Decision Criteria, Collaboration Rules, Constraints, Best Practices, Anti-patterns, Deliverables, Success Criteria.
4. **Claim ownership**: add a row to `docs/collaboration-matrix.md` ownership map. If the new domain overlaps an existing owner, shrink the *new* agent's scope — never edit the existing agent's scope.
5. Add handoff contracts and (if it's a gate) blocking rights to the matrix.
6. Reference only existing skills; if the agent needs new knowledge, add a skill first.

## Adding a new skill

1. Create `skills/<kebab-case-name>/SKILL.md`:
   ```yaml
   ---
   name: <kebab-case-name>
   description: One line stating what the skill teaches and when to load it.
   ---
   ```
2. Body must contain all 7 sections: Purpose, When to Use, Rules, Examples, Best Practices, Common Mistakes, References.
3. Skills are **self-contained**: no skill may require reading another skill to be usable. Cross-reference by name only ("see skill `x`") for optional depth.
4. Skills state *rules and patterns*, never project-specific decisions — those belong in ADRs.
5. List the skill in the relevant agents' "Inputs" section **only when creating those agents**; existing agents pick up new skills via the orchestrator's task brief instead.

## Adding a new command

1. Create `commands/<name>.md` with frontmatter:
   ```yaml
   ---
   description: One line shown in the command picker.
   argument-hint: "[optional args]"
   ---
   ```
2. Body: state which agent(s) to invoke, in what order, with which skills and templates. Use `$ARGUMENTS` for user input.
3. A command orchestrates existing agents — it never contains domain knowledge itself.

## Adding a new template

1. Create `templates/<name>.md`. Every template ends with a **Quality Gates** checklist section referencing `docs/quality-gates.md`.
2. Mention the template in the agent(s) that produce or consume it only if those agents are being created; otherwise the orchestrator's brief points to it.

## Rules that keep the system stable

- Never rename an existing agent, skill, or template — other files reference them by name.
- Never broaden an existing agent's scope to absorb a new need; create a new agent or skill.
- Deprecation: mark with `> DEPRECATED: superseded by <name>` at the top; delete only after nothing references it.
- Any change to `docs/global-rules.md` requires explicit user approval — it rebinds every agent.

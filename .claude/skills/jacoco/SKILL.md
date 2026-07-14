---
name: jacoco
description: Code coverage with JaCoCo — thresholds as CI gates, what to measure and exclude, and reading coverage as a signal rather than a target.
---

# JaCoCo

## Purpose

Measure which code the tests actually exercise, enforce the project floor mechanically in CI, and keep coverage an honest signal — not a number to farm.

## When to Use

Configuring coverage, justifying exclusions, interpreting reports at Gate 3, wiring the CI check.

## Rules

1. **The floor**: ≥ 80% **line** coverage on `domain` and `application` packages, enforced by `jacoco:check` bound to `verify` — CI fails below it (qa-engineer owns the number; nobody edits it to pass a build).
2. **Branch coverage watched on domain rules**: pricing, promotions, stock, and order-transition logic should approach full branch coverage — a missed branch in `DiscountPolicy` is a missed business rule.
3. **Measured scope is business logic**: exclusions are structural-only and justified in the POM comment: generated code/mappers, configuration classes, JPA `*Entity` boilerplate, records that are pure data, the application main class. Excluding a class *because its tests are hard* is forbidden — that's the signal.
4. **Coverage is necessary, not sufficient**: Gate 3 review checks assertion quality alongside the report — 100% coverage with weak assertions fails the gate (see qa-engineer's anti-patterns).
5. Reports generated on every CI run (XML for tooling + HTML artifact); coverage on new/changed code visible in PRs.

## Examples

```xml
<execution>
  <id>check</id>
  <goals><goal>check</goal></goals>
  <configuration>
    <rules>
      <rule>
        <element>BUNDLE</element>
        <includes>
          <include>com.ecommerce.*.domain.*</include>
          <include>com.ecommerce.*.application.*</include>
        </includes>
        <limits>
          <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.80</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```

Reading the report: sort by *missed lines* (not percentage) — a 60%-covered use case with 40 missed lines outranks a 20%-covered 5-line mapper.

## Best Practices

- Merge unit + integration execution data (`jacoco:report` over both) so repository adapters get credit from Testcontainers runs.
- Investigate coverage *drops* in PRs even when above the floor — deleted tests hide there.
- Use the report as a to-do list at Gate 3: each uncovered domain branch is either a missing plan case (→ qa-engineer) or dead code (→ delete).
- Keep exclusion patterns few, alphabetized, and commented with the reason.

## Common Mistakes

- Chasing 100% by testing getters/`toString` while the promotion-stacking branch stays red.
- Lowering the threshold "temporarily" for a deadline (see global prohibitions — it never comes back up).
- Excluding whole packages with a wildcard that quietly swallows a new business class.
- Treating instruction coverage and line coverage interchangeably in reports to the gate — name the counter.
- Coverage on `presentation`/`infrastructure` driving effort that belongs in API/integration tests measured elsewhere.

## References

- jacoco.org docs (check rules, counters)
- See skills `testing-strategy`, `junit`, `testcontainers`

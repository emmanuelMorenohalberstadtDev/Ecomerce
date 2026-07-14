---
name: validation
description: Input validation with Bean Validation (Jakarta) on request DTOs plus domain invariants — layered validation strategy, custom validators, and error mapping.
---

# Validation

## Purpose

Reject bad input at the boundary with clear errors, and make invalid domain states unconstructible — two layers, different jobs.

## When to Use

Designing any request DTO, adding fields, writing custom validators, deciding where a rule belongs.

## Rules

1. **Two layers, both mandatory**:
   - *Boundary (Bean Validation)*: shape checks on request records — presence, format, ranges. Produces 400 with field-level problem details.
   - *Domain (invariants)*: business rules in constructors/factories/methods — `Quantity` positive, order not empty. Throwing domain exceptions → 409/422.
2. Every request DTO field has explicit constraints; an unconstrained `String` field is a review finding.
3. Controllers use `@Valid` on request bodies; path/query params validated via `@Validated` on the controller class.
4. A rule needing repository/database access is **not** a Bean Validation concern — it's use-case/domain logic (e.g., email uniqueness → domain check → 409).
5. Custom constraints for repeated formats: `@ValidSku`, `@ValidCurrency` — one annotation, one validator, reused.
6. Validation error responses: RFC 9457 problem detail with an `errors` array of `{field, code, message}` — codes stable for frontend mapping, messages human-readable.
7. Never trust client-computed values: totals, prices, discount amounts are recalculated server-side regardless of what the request claims.

## Examples

```java
public record CreateOrderRequest(
    @NotEmpty @Size(max = 100) List<@Valid OrderLineRequest> lines,
    @NotNull @Valid AddressRequest shippingAddress,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {}

public record OrderLineRequest(
    @NotBlank String sku,
    @Positive @Max(999) int quantity
) {}
```

```java
// domain layer — invariant, not annotation
public record Quantity(int value) {
    public Quantity { if (value < 1 || value > 999) throw new InvalidQuantityException(value); }
}
```

## Best Practices

- Validate collections deeply (`List<@Valid X>`, `@Size` limits on every list — unbounded lists are a DoS vector).
- Normalize before validating where appropriate (trim, lowercase emails) in DTO compact constructors.
- Keep messages in the default bundle for now; codes (`order.lines.empty`) are the stable contract, not messages.
- Mirror key constraints in frontend forms for UX (see skill `angular`) — but the backend remains the enforcer.

## Common Mistakes

- Validating only in the frontend, or only in the domain (500s instead of 400s), or duplicating complex business rules in annotations.
- `@NotNull` on primitives (can't be null — use the right type).
- Returning the first error only — collect all field errors in one response.
- Custom validators with side effects or repository calls.
- Accepting and silently clamping out-of-range values instead of rejecting.

## References

- Jakarta Bean Validation 3 spec; RFC 9457
- See skills `exception-handling`, `rest-api`, `secure-coding`, `ddd`

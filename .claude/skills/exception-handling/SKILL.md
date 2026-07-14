---
name: exception-handling
description: Exception strategy for the backend — domain exceptions, one global RestControllerAdvice, RFC 9457 Problem Details mapping, and logging discipline.
---

# Exception Handling

## Purpose

Every failure produces exactly one log entry at the right level and one uniform, safe, actionable error response — no stack traces to clients, no swallowed errors.

## When to Use

Creating domain exceptions, mapping errors to HTTP, deciding catch/log/rethrow, reviewing error paths.

## Rules

1. **Domain exceptions** extend a small sealed hierarchy: `DomainException` → `NotFoundException`, `ConflictException`, `BusinessRuleException`. Each carries a stable `type` slug and structured properties — no formatting logic in throw sites.
2. **One `@RestControllerAdvice`** for the whole API maps: domain exceptions → their status/type; `MethodArgumentNotValidException` → 400 with field errors; anything unexpected → 500 generic problem detail. No per-controller try/catch for response shaping.
3. **RFC 9457 shape** always:
   ```json
   { "type": "https://api.ecommerce.dev/problems/insufficient-stock",
     "title": "Insufficient stock", "status": 409,
     "detail": "Requested 5 of SKU-123, only 2 available",
     "instance": "/api/v1/orders", "sku": "SKU-123", "available": 2 }
   ```
4. **Never leak internals**: 500 responses carry a correlation id and a generic message — exception class names, SQL, stack frames stay in logs.
5. **Catch only to act**: translate at a boundary, add context, or recover. Otherwise let it propagate to the advice. Never `catch (Exception e) {}` or log-and-rethrow (double logging).
6. **Logging levels**: 5xx/unexpected → `ERROR` with stack trace + correlation id; expected business failures (409/422) → `WARN` or `INFO` without stack trace; validation 400s → not logged individually (metrics instead).
7. Exceptions are exceptional: expected alternate outcomes inside the domain (payment declined) may be modeled as result types (sealed `PaymentResult`) rather than exceptions — decide per aggregate, consistently.

## Examples

```java
public final class InsufficientStockException extends ConflictException {
    public InsufficientStockException(Sku sku, int requested, int available) {
        super("insufficient-stock", Map.of("sku", sku.value(),
              "requested", requested, "available", available));
    }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) { return ex.toProblemDetail(HttpStatus.CONFLICT); }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) { /* log ERROR w/ correlationId; return generic 500 */ }
}
```

## Best Practices

- Correlation id per request (filter + MDC) included in every log line and every 500 body — support can find the stack trace from the user's error.
- Keep a registry of problem `type` slugs in the API docs; frontend maps types → UX (see collaboration with documentation-engineer).
- Test error contracts by asserting on `type` + `status`, not message text.

## Common Mistakes

- Returning 500 for not-found or business conflicts because nothing mapped the exception.
- Messages built for developers shown to users, or worse, user-facing messages containing entity internals.
- Wrapping every exception in `RuntimeException(e)` losing the type the advice needs.
- Different error JSON shapes between security layer (401/403) and application layer — wire the entry point/denied handler to problem details too (see skill `spring-security`).

## References

- RFC 9457; Spring `ProblemDetail` support (Spring 6)
- See skills `rest-api`, `validation`, `spring-security`

package com.ecommerce.common.web.exception;

/**
 * Signals that a caller-supplied input or action violates a domain business rule — the request is
 * syntactically valid but semantically unprocessable.
 *
 * <p>Maps to HTTP 422 Unprocessable Content. Distinct from {@code ConflictException} (state clash)
 * and from {@code MethodArgumentNotValidException} (Bean Validation, 400).
 *
 * <p>Examples: attempting to apply a coupon that has already been used by this customer; exceeding
 * maximum cart line quantity; submitting a payment after the checkout window has expired.
 *
 * <p>Context-specific subtypes (e.g. {@code PaymentWindowExpiredException}) are {@code final} and
 * live in their bounded context's {@code domain.exception} package.
 */
public non-sealed class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}

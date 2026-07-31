package com.ecommerce.common.web.exception;

/**
 * Root of the domain exception hierarchy.
 *
 * <p>Sealed so that every permitted leaf is known at compile time — no unintended subclassing
 * outside this package. Each permitted type carries a stable, intention-revealing name that the
 * {@code ApiExceptionHandler} maps to an HTTP status without pattern-matching on message text.
 *
 * <p>Instances carry no Spring/JPA/Jackson imports — this class is framework-free by design and
 * satisfies the {@code domain_is_framework_free} ArchUnit rule when subclassed in domain packages.
 * The exception hierarchy lives in {@code common.web.exception} (cross-cutting infrastructure)
 * rather than in a single bounded context so that all contexts can extend the same leaves.
 */
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, BusinessRuleViolationException,
                AuthenticationException, TokenException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

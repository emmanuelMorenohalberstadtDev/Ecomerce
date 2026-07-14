package com.ecommerce.common.exception;

import java.util.Map;

/**
 * Root of the domain exception hierarchy (backend-architecture.md §5).
 * Carries a stable RFC 9457 {@code type} slug and structured properties;
 * throw sites never format responses — the web advice does (framework-free by design,
 * so domain code across contexts may extend the subclasses).
 */
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, BusinessRuleException {

    private final String type;
    private final Map<String, Object> properties;

    protected DomainException(String type, String detail, Map<String, Object> properties) {
        super(detail);
        this.type = type;
        this.properties = Map.copyOf(properties);
    }

    public String type() {
        return type;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}

package com.ecommerce.common.exception;

import java.util.Map;

/** Input is well-formed but violates a business rule. Maps to 422. */
public abstract non-sealed class BusinessRuleException extends DomainException {

    protected BusinessRuleException(String type, String detail, Map<String, Object> properties) {
        super(type, detail, properties);
    }
}

package com.ecommerce.common.exception;

import java.util.Map;

/** Request conflicts with current state (stock, duplicates, illegal transitions). Maps to 409. */
public abstract non-sealed class ConflictException extends DomainException {

    protected ConflictException(String type, String detail, Map<String, Object> properties) {
        super(type, detail, properties);
    }
}

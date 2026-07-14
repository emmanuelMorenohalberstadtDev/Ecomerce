package com.ecommerce.common.exception;

import java.util.Map;

/** Requested resource does not exist or is not visible to the caller. Maps to 404. */
public abstract non-sealed class NotFoundException extends DomainException {

    protected NotFoundException(String type, String detail, Map<String, Object> properties) {
        super(type, detail, properties);
    }
}

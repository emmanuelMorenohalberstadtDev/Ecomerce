package com.ecommerce.cart.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when the caller's identity (guest cart token or authenticated customer) does not
 * resolve to an ACTIVE cart.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}. Every cart endpoint resolves "my cart"
 * purely from the caller's own identity (guest cookie hash lookup or JWT-derived customer id) —
 * there is no cart id in any cart URL, so there is no separate "wrong owner" case to fold into
 * this 404 the way {@code security-architecture.md §3.3} describes for id-addressed resources;
 * "no cart for you" and "not yours" collapse into the same case by construction here.
 */
public final class CartNotFoundException extends NotFoundException {

    public CartNotFoundException(String message) {
        super(message);
    }
}

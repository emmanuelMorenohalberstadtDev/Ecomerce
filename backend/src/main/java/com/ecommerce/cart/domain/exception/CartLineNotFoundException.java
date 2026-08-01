package com.ecommerce.cart.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when {@code updateLineQuantity} or {@code removeLine} targets a product that is not a
 * line in the resolved cart.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler} — the addressed sub-resource
 * ({@code /api/v1/carts/me/items/{productId}}) does not exist, standard REST semantics. Not an
 * ownership/enumeration concern (product ids are public catalog identifiers, not secrets), so
 * unlike {@link CartNotFoundException} there is no anti-enumeration nuance here.
 */
public final class CartLineNotFoundException extends NotFoundException {

    public CartLineNotFoundException(String message) {
        super(message);
    }
}

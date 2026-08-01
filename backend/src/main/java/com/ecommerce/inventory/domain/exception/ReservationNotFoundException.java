package com.ecommerce.inventory.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when a {@link com.ecommerce.inventory.domain.model.StockReservation} referenced by id
 * (commit, release, or any future direct lookup) does not exist.
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public final class ReservationNotFoundException extends NotFoundException {

    public ReservationNotFoundException(String message) {
        super(message);
    }
}

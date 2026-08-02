package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when {@code checkout.domain.port.out.ReservationPort#reserve} cannot fully reserve stock
 * for a checkout session's lines — translated by
 * {@code checkout.infrastructure.adapter.ReservationAdapter} from inventory's façade-owned
 * {@code StockReservationPort.InsufficientStockException}, which checkout must not import
 * directly (ADR-0003 §Decision item 2). Mirrors
 * {@code inventory.domain.exception.InsufficientStockException} exactly (a state conflict — the
 * world changed under the caller — rather than a business-rule violation).
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s generic {@code ConflictException} handler.
 */
public final class InsufficientStockForCheckoutException extends ConflictException {

    public InsufficientStockForCheckoutException(String message) {
        super(message);
    }
}

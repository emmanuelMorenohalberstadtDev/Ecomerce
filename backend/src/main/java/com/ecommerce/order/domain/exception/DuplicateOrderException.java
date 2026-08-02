package com.ecommerce.order.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown by the JPA adapter when creating an {@link com.ecommerce.order.domain.model.Order} loses
 * the race against {@code uq_orders_reservation} — the unique constraint on
 * {@code orders.reservation_id} ({@code V006__create_order_schema.sql}: "also gives a DB-level
 * backstop against a duplicate order being created if the AFTER_COMMIT listener were ever invoked
 * twice for the same reservation"). Backstops
 * {@code PlaceOrderFromCheckoutUseCase}'s own up-front {@code findByReservationId} idempotency
 * check for the narrow window between that lookup and this insert — mirrors
 * {@code checkout.domain.exception.DuplicateIdempotencyKeyException} exactly.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic {@code ConflictException}
 * handler.
 */
public final class DuplicateOrderException extends ConflictException {

    public DuplicateOrderException(String message) {
        super(message);
    }
}

package com.ecommerce.payment.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown by the JPA adapter when creating a {@link com.ecommerce.payment.domain.model.Payment}
 * loses the race against {@code uq_payments_order} — the unique constraint on
 * {@code payments.order_id} ({@code V007__create_payment_schema.sql}: "exactly one Payment per
 * order"). Backstops {@code SubmitPaymentUseCase}'s own up-front
 * {@code PaymentRepository.findByOrderIdAndCustomerId} lookup for the narrow window between that
 * read and this insert — mirrors {@code order.domain.exception.DuplicateOrderException} exactly.
 *
 * <p>Maps to HTTP 409 via {@code ApiExceptionHandler}'s existing generic {@code ConflictException}
 * handler.
 */
public final class DuplicatePaymentException extends ConflictException {

    public DuplicatePaymentException(String message) {
        super(message);
    }
}

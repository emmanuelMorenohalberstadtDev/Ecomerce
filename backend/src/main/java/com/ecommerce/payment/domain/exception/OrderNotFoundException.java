package com.ecommerce.payment.domain.exception;

import com.ecommerce.common.web.exception.NotFoundException;

/**
 * Thrown when the order a payment attempt targets does not exist, or exists but does not belong
 * to the requesting customer — payment's own proper domain exception, translated by
 * {@code payment.infrastructure.adapter.OrderAdapter} from
 * {@code order.application.port.OrderPaymentPort.OrderNotFoundException} (payment must never
 * import order's internal {@code domain.exception} type, ADR-0005 §Decision item 1). Mirrors
 * {@code order.domain.exception.OrderNotFoundException}'s own "ownership failures are 404, never
 * 403" reasoning (security-architecture §3.2/§3.3).
 *
 * <p>Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public final class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}

package com.ecommerce.payment.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when a payment attempt targets an order that is not currently {@code PLACED} — payment's
 * own proper domain exception (ADR-0005 item 2/3), used two ways:
 * <ul>
 *   <li>{@code SubmitPaymentUseCase}'s own fast-fail read-time check against
 *       {@code payment.domain.port.out.OrderPort.OrderView#payable()}, thrown directly, <em>before</em>
 *       the gateway is ever called — a UX/cost optimization (ADR-0005 §Consequences), not the
 *       correctness guarantee.</li>
 *   <li>Translated by {@code payment.infrastructure.adapter.OrderAdapter} from
 *       {@code order.application.port.OrderPaymentPort.OrderNotPayableException} — the correctness
 *       guarantee, backed by {@code Order.markPaid}'s own state-transition guard.</li>
 * </ul>
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}'s generic {@code BusinessRuleViolationException}
 * handler.
 */
public final class OrderNotPayableException extends BusinessRuleViolationException {

    public OrderNotPayableException(String message) {
        super(message);
    }
}

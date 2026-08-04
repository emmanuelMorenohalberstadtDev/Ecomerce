package com.ecommerce.payment.domain.model;

/**
 * The result of one charge attempt against {@link com.ecommerce.payment.domain.port.out.PaymentGatewayPort}
 * (ADR-0005 item 2), matching {@code ck_payment_attempts_outcome} in
 * {@code V007__create_payment_schema.sql}.
 *
 * <p>{@link Payment#markCaptured} is only ever called after an {@code APPROVED} outcome;
 * {@code DECLINED}/{@code TIMEOUT} leave the parent {@link Payment#getStatus()} untouched at
 * {@link PaymentStatus#PENDING} so the attempt is retryable within the checkout payment window.
 */
public enum PaymentOutcome {
    APPROVED,
    DECLINED,
    TIMEOUT
}

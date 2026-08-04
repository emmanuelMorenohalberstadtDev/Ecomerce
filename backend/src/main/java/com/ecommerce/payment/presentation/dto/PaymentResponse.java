package com.ecommerce.payment.presentation.dto;

/**
 * Wire shape for a submitted-payment outcome, returned by
 * {@code POST /api/v1/orders/{orderId}/payments} (ADR-0005).
 *
 * <p>{@code status} is the {@code Payment} aggregate's current status ({@code PENDING}/
 * {@code CAPTURED}/{@code REFUNDED}); {@code outcome} is this specific attempt's gateway result
 * ({@code APPROVED}/{@code DECLINED}/{@code TIMEOUT}) — the two are distinct on purpose: a
 * {@code DECLINED} attempt leaves the payment {@code PENDING}, retryable. {@code declineReason}
 * and {@code gatewayReference} are nullable.
 */
public record PaymentResponse(
        String paymentId,
        String status,
        String outcome,
        String declineReason,
        String gatewayReference,
        MoneyDto amount,
        String attemptedAt
) {}

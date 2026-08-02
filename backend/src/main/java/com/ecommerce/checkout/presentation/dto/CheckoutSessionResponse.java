package com.ecommerce.checkout.presentation.dto;

/**
 * Wire shape for a checkout session, returned by every checkout-sessions endpoint
 * (api-guidelines.md §2: "return what the client needs next").
 *
 * <p>{@code reservationId} and {@code paymentDeadline} are {@code null} while
 * {@code PENDING_CONFIRMATION} — no reservation exists yet (ADR-0003 §Decision item 3/4).
 * {@code paymentDeadline} is ISO-8601 UTC (api-guidelines §1.6).
 */
public record CheckoutSessionResponse(
        String id,
        String status,
        String cartId,
        String reservationId,
        MoneyDto subtotal,
        MoneyDto discountTotal,
        MoneyDto shippingFee,
        MoneyDto grandTotal,
        MoneyDto expectedTotal,
        String paymentDeadline,
        String createdAt,
        String updatedAt
) {}

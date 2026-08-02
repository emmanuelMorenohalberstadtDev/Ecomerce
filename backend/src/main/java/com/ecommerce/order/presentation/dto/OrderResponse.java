package com.ecommerce.order.presentation.dto;

import java.util.List;

/**
 * Wire shape for order detail, returned by {@code GET /api/v1/orders/{id}} and every order
 * lifecycle mutation endpoint (api-guidelines.md §2: "return what the client needs next").
 *
 * <p>{@code reservationId} is deliberately NOT included — domain-model.md §2: "commit/release
 * only, never displayed" (ADR-0004). {@code couponCode} is nullable (most orders carry none).
 */
public record OrderResponse(
        String id,
        String status,
        List<OrderLineResponse> lines,
        MoneyDto itemsTotal,
        MoneyDto discountTotal,
        MoneyDto shippingTotal,
        MoneyDto grandTotal,
        String couponCode,
        List<OrderStatusTransitionResponse> statusHistory,
        String placedAt,
        String updatedAt
) {}

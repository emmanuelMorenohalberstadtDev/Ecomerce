package com.ecommerce.order.presentation.dto;

/**
 * Wire shape for one row of {@code GET /api/v1/orders} (order history list) — matches
 * {@code idx_orders_customer_placed}'s covering columns exactly (id, status, grand total,
 * placed-at); no line/history detail (fetch {@code GET /api/v1/orders/{id}} for that).
 */
public record OrderSummaryResponse(String id, String status, MoneyDto grandTotal, String placedAt) {}

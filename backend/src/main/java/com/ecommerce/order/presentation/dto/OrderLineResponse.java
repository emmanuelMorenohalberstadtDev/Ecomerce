package com.ecommerce.order.presentation.dto;

/**
 * Wire shape for one order line — a frozen {@code LineSnapshot} (rule 3), never re-priced.
 */
public record OrderLineResponse(String productId, String productName, MoneyDto unitPrice, int quantity) {}

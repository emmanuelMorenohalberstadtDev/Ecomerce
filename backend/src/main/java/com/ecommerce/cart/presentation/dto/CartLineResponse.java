package com.ecommerce.cart.presentation.dto;

/** Wire shape for one cart line. {@code productId} doubles as the line's addressable sub-resource id. */
public record CartLineResponse(
        String productId,
        String productName,
        MoneyDto unitPrice,
        int quantity,
        boolean unavailable,
        MoneyDto lineTotal
) {}

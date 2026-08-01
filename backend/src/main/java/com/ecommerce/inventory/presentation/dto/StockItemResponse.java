package com.ecommerce.inventory.presentation.dto;

/** Wire shape for a stock item, returned by the admin stock-adjustment endpoint. */
public record StockItemResponse(String productId, int quantityAvailable, long version) {}

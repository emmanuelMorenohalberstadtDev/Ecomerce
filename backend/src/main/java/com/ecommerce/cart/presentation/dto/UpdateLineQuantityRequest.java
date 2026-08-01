package com.ecommerce.cart.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Request body for {@code PUT /api/v1/carts/me/items/{productId}}. */
public record UpdateLineQuantityRequest(

        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 99, message = "quantity must not exceed 99")
        int quantity

) {}

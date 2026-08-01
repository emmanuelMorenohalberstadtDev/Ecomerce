package com.ecommerce.cart.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request body for {@code POST /api/v1/carts/me/items}. */
public record AddItemRequest(

        @NotBlank(message = "productId must not be blank")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "productId must be a valid UUID")
        String productId,

        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 99, message = "quantity must not exceed 99")
        int quantity

) {}

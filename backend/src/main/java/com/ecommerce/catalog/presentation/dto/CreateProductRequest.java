package com.ecommerce.catalog.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request body for {@code POST /api/v1/admin/products}. */
public record CreateProductRequest(

        @NotBlank(message = "categoryId must not be blank")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "categoryId must be a valid UUID")
        String categoryId,

        @NotBlank(message = "sku must not be blank")
        @Size(min = 1, max = 64, message = "sku must be between 1 and 64 characters")
        String sku,

        @NotNull(message = "basePrice must be provided")
        @Valid
        MoneyDto basePrice,

        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must not exceed 255 characters")
        String name,

        @Size(max = 10000, message = "description must not exceed 10000 characters")
        String description,

        @Size(max = 20, message = "images must not exceed 20 entries")
        List<@Valid ProductImageDto> images

) {}

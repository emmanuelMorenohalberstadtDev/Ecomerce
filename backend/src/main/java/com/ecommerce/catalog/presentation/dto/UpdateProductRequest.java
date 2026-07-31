package com.ecommerce.catalog.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code PUT /api/v1/admin/products/{id}}. SKU, price, and status are not
 * editable through this endpoint — see {@code ChangeProductPriceRequest} and the dedicated
 * retirement sub-resource.
 */
public record UpdateProductRequest(

        @NotBlank(message = "categoryId must not be blank")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "categoryId must be a valid UUID")
        String categoryId,

        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must not exceed 255 characters")
        String name,

        @Size(max = 10000, message = "description must not exceed 10000 characters")
        String description,

        @Size(max = 20, message = "images must not exceed 20 entries")
        List<@Valid ProductImageDto> images

) {}

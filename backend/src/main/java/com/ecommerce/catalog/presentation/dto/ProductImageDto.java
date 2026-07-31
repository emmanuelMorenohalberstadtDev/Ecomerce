package com.ecommerce.catalog.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Wire shape for one product image at a fixed render position. */
public record ProductImageDto(

        @NotBlank(message = "imageUrl must not be blank")
        @Size(max = 2048, message = "imageUrl must not exceed 2048 characters")
        String imageUrl,

        @PositiveOrZero(message = "displayOrder must be >= 0")
        int displayOrder

) {}

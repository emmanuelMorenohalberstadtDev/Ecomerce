package com.ecommerce.catalog.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code PUT /api/v1/admin/products/{id}/price}. */
public record ChangeProductPriceRequest(

        @NotNull(message = "basePrice must be provided")
        @Valid
        MoneyDto basePrice

) {}

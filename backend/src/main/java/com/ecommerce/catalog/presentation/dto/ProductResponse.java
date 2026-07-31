package com.ecommerce.catalog.presentation.dto;

import java.time.Instant;
import java.util.List;

/** Wire shape for a product, returned by both public and admin product endpoints. */
public record ProductResponse(
        String id,
        String categoryId,
        String sku,
        String status,
        MoneyDto basePrice,
        String name,
        String description,
        List<ProductImageDto> images
) {}

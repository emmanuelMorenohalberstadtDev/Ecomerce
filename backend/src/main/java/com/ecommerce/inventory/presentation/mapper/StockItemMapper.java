package com.ecommerce.inventory.presentation.mapper;

import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.inventory.presentation.dto.StockItemResponse;

/**
 * Pure domain-to-DTO conversion for {@link StockItem}. No behavior, no framework imports beyond
 * what the DTO itself requires — keeps JPA entities and domain objects from leaking into the wire
 * format directly (mirrors catalog's {@code ProductMapper}).
 */
public final class StockItemMapper {

    private StockItemMapper() {}

    public static StockItemResponse toResponse(StockItem stockItem) {
        return new StockItemResponse(
                stockItem.getProductId().toString(),
                stockItem.getAvailable().value(),
                stockItem.getVersion());
    }
}

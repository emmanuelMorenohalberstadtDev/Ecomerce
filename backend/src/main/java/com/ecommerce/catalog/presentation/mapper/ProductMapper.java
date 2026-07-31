package com.ecommerce.catalog.presentation.mapper;

import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.ProductImage;
import com.ecommerce.catalog.presentation.dto.MoneyDto;
import com.ecommerce.catalog.presentation.dto.ProductImageDto;
import com.ecommerce.catalog.presentation.dto.ProductResponse;
import com.ecommerce.shared.money.Money;

/**
 * Pure domain-to-DTO conversion for {@link Product}. No behavior, no framework imports beyond
 * what the DTOs themselves require — keeps JPA entities and domain objects from leaking into
 * the wire format directly.
 */
public final class ProductMapper {

    private ProductMapper() {}

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId().toString(),
                product.getCategoryId().toString(),
                product.getSku().value(),
                product.getStatus().name(),
                toMoneyDto(product.getBasePrice()),
                product.getName(),
                product.getDescription(),
                product.getImages().stream().map(ProductMapper::toImageDto).toList()
        );
    }

    public static MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency());
    }

    public static ProductImageDto toImageDto(ProductImage image) {
        return new ProductImageDto(image.imageUrl(), image.displayOrder());
    }
}

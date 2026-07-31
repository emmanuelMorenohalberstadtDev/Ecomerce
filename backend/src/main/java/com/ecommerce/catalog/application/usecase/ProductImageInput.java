package com.ecommerce.catalog.application.usecase;

/**
 * Application-layer input shape for one product image, shared by {@link CreateProductUseCase}
 * and {@link UpdateProductUseCase} commands.
 *
 * <p>Deliberately a separate small record rather than the domain's
 * {@link com.ecommerce.catalog.domain.model.ProductImage} directly — keeps the use-case command
 * surface decoupled from domain invariant enforcement, which happens when
 * {@code Product.create}/{@code updateDetails} builds the real value object.
 */
public record ProductImageInput(String imageUrl, int displayOrder) {}

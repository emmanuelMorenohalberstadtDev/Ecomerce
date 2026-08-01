package com.ecommerce.catalog.infrastructure.adapter;

import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.id.ProductId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Thin translation adapter implementing {@link ProductLookupPort} on top of catalog's existing
 * {@link ProductRepository} domain port — no new business logic, just a projection from the full
 * {@link Product} aggregate to the small {@link ProductLookupPort.ProductSummary} shape other
 * contexts are allowed to see.
 *
 * <p><strong>Wiring note:</strong> this class is annotated {@code @Component} directly, rather
 * than wired via an explicit {@code @Bean} method in {@code CatalogPersistenceConfiguration}
 * (the house pattern {@code JpaProductRepository}/{@code JpaCategoryRepository} follow). This is
 * a deliberate, narrow deviation: the change that introduced this class is scoped to add exactly
 * two new files to the catalog context (this adapter and {@link ProductLookupPort}), additive
 * only, with no edits to any existing catalog file — including
 * {@code CatalogPersistenceConfiguration}. Component-scanning (the application's base package is
 * {@code com.ecommerce}, which covers {@code catalog.infrastructure.adapter}) registers this
 * bean without touching that file. {@link ProductRepository} is already a registered bean
 * (via {@code CatalogPersistenceConfiguration}), so constructor injection here works identically
 * either way.
 */
@Component
public class ProductLookupAdapter implements ProductLookupPort {

    private final ProductRepository productRepository;

    public ProductLookupAdapter(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository);
    }

    @Override
    public Optional<ProductSummary> findActiveById(ProductId id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .map(p -> new ProductSummary(p.getId(), p.getName(), p.getBasePrice()));
    }
}

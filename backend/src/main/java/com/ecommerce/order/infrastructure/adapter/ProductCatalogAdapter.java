package com.ecommerce.order.infrastructure.adapter;

import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.order.domain.port.out.ProductCatalogPort;
import com.ecommerce.shared.id.ProductId;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements order's {@link ProductCatalogPort} by delegating to catalog's public
 * {@link ProductLookupPort} — the only dependency this adapter (and, transitively, the order
 * context) has on the catalog context. Mirrors
 * {@code cart.infrastructure.adapter.ProductCatalogAdapter} exactly (ADR-0004 §Decision item 1:
 * "the third independently-named consumer mirror ... no new pattern").
 */
public class ProductCatalogAdapter implements ProductCatalogPort {

    private final ProductLookupPort productLookupPort;

    public ProductCatalogAdapter(ProductLookupPort productLookupPort) {
        this.productLookupPort = Objects.requireNonNull(productLookupPort);
    }

    @Override
    public Optional<CatalogProductView> findActiveProduct(ProductId productId) {
        return productLookupPort.findActiveById(productId)
                .map(summary -> new CatalogProductView(summary.id(), summary.name(), summary.price()));
    }
}

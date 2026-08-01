package com.ecommerce.pricing.infrastructure.adapter;

import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.pricing.domain.port.out.ProductCatalogPort;
import com.ecommerce.shared.id.ProductId;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements pricing's {@link ProductCatalogPort} by delegating to catalog's public
 * {@link ProductLookupPort} — the only dependency this adapter (and, transitively, the pricing
 * context) has on the catalog context, satisfying
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events} both in letter
 * ({@code application.port} is not a forbidden target package) and in spirit (catalog's
 * {@code application.port} is its deliberate public façade; this adapter never touches catalog's
 * {@code domain}/{@code infrastructure}/{@code presentation} packages).
 *
 * <p>{@link ProductLookupPort#findActiveById} already returns price via {@code ProductSummary} —
 * sufficient as-is, no catalog changes needed, no bulk/batch lookup added (called once per line).
 */
public class CatalogPriceAdapter implements ProductCatalogPort {

    private final ProductLookupPort productLookupPort;

    public CatalogPriceAdapter(ProductLookupPort productLookupPort) {
        this.productLookupPort = Objects.requireNonNull(productLookupPort);
    }

    @Override
    public Optional<PricedProductView> findActiveById(ProductId productId) {
        return productLookupPort.findActiveById(productId)
                .map(summary -> new PricedProductView(summary.id(), summary.price()));
    }
}

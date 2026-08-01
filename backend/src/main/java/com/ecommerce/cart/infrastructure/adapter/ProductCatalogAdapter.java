package com.ecommerce.cart.infrastructure.adapter;

import com.ecommerce.cart.domain.port.out.ProductCatalogPort;
import com.ecommerce.catalog.application.port.ProductLookupPort;
import com.ecommerce.shared.id.ProductId;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements cart's {@link ProductCatalogPort} by delegating to catalog's public
 * {@link ProductLookupPort} — the only dependency this adapter (and, transitively, the cart
 * context) has on the catalog context, satisfying
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events} both in letter
 * ({@code application.port} is not a forbidden target package) and in spirit (catalog's
 * {@code application.port} is its deliberate public façade; this adapter never touches catalog's
 * {@code domain}/{@code infrastructure}/{@code presentation} packages).
 *
 * <p>Standard cross-context Spring bean wiring within one application context — the same pattern
 * already used for cross-context event publishing (e.g. {@code UserAuthenticatedEvent}).
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

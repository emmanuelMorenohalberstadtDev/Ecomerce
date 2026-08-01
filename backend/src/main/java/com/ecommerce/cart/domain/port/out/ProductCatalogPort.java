package com.ecommerce.cart.domain.port.out;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.util.Optional;

/**
 * Cart's own outbound port representing what it needs from the catalog context — deliberately
 * cart-shaped, not a re-export of catalog's {@code ProductLookupPort}
 * ({@code ProductLookupPort.ProductSummary}), so cart never depends on a catalog presentation/
 * wire type and the two contexts can evolve their shapes independently.
 *
 * <p>Implemented by {@code cart.infrastructure.adapter.ProductCatalogAdapter}, whose only
 * dependency on catalog is the catalog-owned
 * {@code com.ecommerce.catalog.application.port.ProductLookupPort} bean — the sanctioned
 * crossing point (application.port), matching how cross-context event publishing already works
 * in this codebase.
 *
 * <p>Serves both of cart's catalog reads with one method: at add-to-cart time, an empty result
 * means "reject the add" ({@code ProductNotAvailableException}); at view-cart time, an empty
 * result for a product that already has a cart line means "flag that line unavailable" (rule 10).
 */
public interface ProductCatalogPort {

    Optional<CatalogProductView> findActiveProduct(ProductId productId);

    /** Minimal, read-only view of an ACTIVE product, shaped for cart's own needs (a snapshot's inputs). */
    record CatalogProductView(ProductId productId, String name, Money price) {

        public CatalogProductView {
            if (productId == null) {
                throw new IllegalArgumentException("CatalogProductView productId must not be null");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("CatalogProductView name must not be null or blank");
            }
            if (price == null) {
                throw new IllegalArgumentException("CatalogProductView price must not be null");
            }
        }
    }
}

package com.ecommerce.pricing.domain.port.out;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.util.Optional;

/**
 * Pricing's own outbound port for resolving a product's current base price from catalog —
 * deliberately pricing-shaped, not a re-export of catalog's
 * {@code catalog.application.port.ProductLookupPort.ProductSummary}. This mirrors why
 * {@code cart.domain.port.out.ProductCatalogPort} exists as cart's own catalog-shaped port
 * instead of depending on catalog's wire type directly: it lets pricing and catalog evolve their
 * shapes independently.
 *
 * <p>Implemented by {@code pricing.infrastructure.adapter.CatalogPriceAdapter}, whose only
 * dependency on catalog is the catalog-owned
 * {@code com.ecommerce.catalog.application.port.ProductLookupPort} bean — the sanctioned
 * crossing point ({@code application.port}), matching how cart already consumes catalog.
 *
 * <p><strong>No product name:</strong> {@link PricedProductView} carries only {@code productId}
 * and {@code basePrice}. Pricing never displays anything to a user, it only computes — carrying a
 * display name here would be dead data. Deliberate YAGNI cut.
 *
 * <p><strong>ACTIVE-only, by design:</strong> {@link #findActiveById} returns
 * {@link Optional#empty()} for a RETIRED (or nonexistent) product, mirroring catalog's own
 * {@code ProductLookupPort.findActiveById} convention. Both cases map to
 * {@code ProductNotAvailableException} in the calling use case.
 */
public interface ProductCatalogPort {

    /**
     * Looks up a product's current base price, but only if it is currently ACTIVE.
     *
     * @param productId the product id
     * @return the product's priced view if it exists and is ACTIVE; {@link Optional#empty()}
     *         otherwise (nonexistent or RETIRED — both look identical to the caller, by design)
     */
    Optional<PricedProductView> findActiveById(ProductId productId);

    /** Minimal, read-only view of an ACTIVE product's price, shaped for pricing's own needs. */
    record PricedProductView(ProductId productId, Money basePrice) {

        public PricedProductView {
            if (productId == null) {
                throw new IllegalArgumentException("PricedProductView productId must not be null");
            }
            if (basePrice == null) {
                throw new IllegalArgumentException("PricedProductView basePrice must not be null");
            }
        }
    }
}

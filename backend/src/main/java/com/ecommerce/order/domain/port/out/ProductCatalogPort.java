package com.ecommerce.order.domain.port.out;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.util.Optional;

/**
 * Order's own outbound port representing what it needs from the catalog context — deliberately
 * order-shaped (ADR-0004 §Decision item 1), not a re-export of catalog's
 * {@code ProductLookupPort.ProductSummary}. The third independently-named consumer mirror of
 * {@code catalog.application.port.ProductLookupPort}, following
 * {@code cart.domain.port.out.ProductCatalogPort} and {@code pricing}'s equivalent exactly — no
 * new pattern (ADR-0003 §Decision item 2).
 *
 * <p>Implemented by {@code order.infrastructure.adapter.ProductCatalogAdapter}, whose only
 * dependency on catalog is the catalog-owned
 * {@code com.ecommerce.catalog.application.port.ProductLookupPort} bean — the sanctioned crossing
 * point (application.port).
 *
 * <p>Used once per line by {@code PlaceOrderFromCheckoutUseCase} to resolve the product's display
 * name for its {@code LineSnapshot} — price and quantity come from
 * {@code CheckoutAwaitingPaymentEvent.Line}, never from this port (rule 3: never re-priced).
 * Because {@code findActiveById} is ACTIVE-only by design, a product retired in the narrow window
 * between checkout's commit and order's {@code AFTER_COMMIT} listener firing returns
 * {@link Optional#empty()}; the caller treats this as non-fatal and falls back to a placeholder
 * name rather than failing order placement (ADR-0004 §Decision item 1).
 */
public interface ProductCatalogPort {

    Optional<CatalogProductView> findActiveProduct(ProductId productId);

    /** Minimal, read-only view of an ACTIVE product, shaped for order's own needs (a display-name source). */
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

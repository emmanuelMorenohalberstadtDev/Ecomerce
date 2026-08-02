package com.ecommerce.checkout.domain.port.out;

import com.ecommerce.checkout.domain.exception.ProductUnavailableForCheckoutException;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

import java.util.List;
import java.util.Objects;

/**
 * Checkout's own outbound port representing what it needs from the pricing context —
 * deliberately checkout-shaped, returning checkout's own {@link RecalculatedTotal} rather than
 * pricing's {@code PriceQuotePort.PriceQuote} (ADR-0003 §Decision item 2, mirroring
 * {@code pricing.domain.port.out.ProductCatalogPort}'s relationship to catalog's
 * {@code ProductLookupPort}).
 *
 * <p>Implemented by {@code checkout.infrastructure.adapter.PriceQuoteAdapter}, whose only
 * dependency on pricing is the pricing-owned
 * {@code com.ecommerce.pricing.application.port.PriceCalculationPort} bean — the sanctioned
 * crossing point. That adapter is also where {@code PriceQuote} -> {@link RecalculatedTotal}
 * translation happens; checkout's application/domain layers never see a pricing type.
 */
public interface PriceQuotePort {

    /**
     * @param lines the (product, quantity) lines to price; must be non-empty
     * @return checkout's own recalculated total for the given lines
     * @throws ProductUnavailableForCheckoutException if any line names a product catalog reports
     *         as nonexistent or not ACTIVE — translated from pricing's façade-owned
     *         {@code PriceCalculationPort.ProductUnavailableException}
     */
    RecalculatedTotal recalculate(List<PriceQuoteLine> lines);

    /** One (product, quantity) line to price. */
    record PriceQuoteLine(ProductId productId, Quantity quantity) {

        public PriceQuoteLine {
            Objects.requireNonNull(productId, "PriceQuoteLine productId must not be null");
            Objects.requireNonNull(quantity, "PriceQuoteLine quantity must not be null");
        }
    }
}

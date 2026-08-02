package com.ecommerce.pricing.application.port;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.util.List;
import java.util.Objects;

/**
 * Outbound-facing public façade pricing exposes to other bounded contexts that need an
 * authoritative price for a set of (product, quantity) lines, without depending on pricing's
 * internal domain model ({@code PriceBreakdown}, {@code PricedLine}, {@code EffectivePrice}) or
 * its concrete use case ({@code CalculateEffectivePriceUseCase} — a forbidden target per
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}, ADR-0003).
 *
 * <p><strong>Why this exists:</strong> checkout needs to recalculate the authoritative total for a
 * cart's lines at checkout start and at re-confirmation (server-side price authority,
 * security-architecture §1.4 threat 2) — this façade is the only sanctioned crossing point.
 * Mirrors {@code catalog.application.port.ProductLookupPort} exactly.
 *
 * <p>Implemented by {@code pricing.infrastructure.adapter.PriceCalculationAdapter}, which wraps
 * {@code CalculateEffectivePriceUseCase} — pricing has no persistence of its own, so there is no
 * transaction boundary to preserve here (unlike
 * {@code inventory.application.port.StockReservationPort}, which delegates to a
 * {@code @Transactional} use case).
 */
public interface PriceCalculationPort {

    /**
     * @param lines the (product, quantity) lines to price; must be non-empty
     * @return the composed price quote for the given lines
     * @throws ProductUnavailableException if any line names a product catalog reports as
     *         nonexistent or not ACTIVE (retired) — translated from pricing's own
     *         {@code ProductNotAvailableException}, which cross-context callers must never import
     */
    PriceQuote calculate(List<PriceLineRequest> lines);

    /** One (product, quantity) line to price. */
    record PriceLineRequest(ProductId productId, Quantity quantity) {

        public PriceLineRequest {
            Objects.requireNonNull(productId, "PriceLineRequest productId must not be null");
            Objects.requireNonNull(quantity, "PriceLineRequest quantity must not be null");
        }
    }

    /** The composed price for a set of lines — subtotal, discount, shipping, and grand total. */
    record PriceQuote(List<PricedLineView> lines, Money subtotal, Money discountTotal,
                       Money shippingFee, Money grandTotal) {

        public PriceQuote {
            Objects.requireNonNull(lines, "PriceQuote lines must not be null");
            Objects.requireNonNull(subtotal, "PriceQuote subtotal must not be null");
            Objects.requireNonNull(discountTotal, "PriceQuote discountTotal must not be null");
            Objects.requireNonNull(shippingFee, "PriceQuote shippingFee must not be null");
            Objects.requireNonNull(grandTotal, "PriceQuote grandTotal must not be null");
            lines = List.copyOf(lines);
        }
    }

    /** One priced line's resolved unit-final price and line total. */
    record PricedLineView(ProductId productId, Quantity quantity, Money unitFinalPrice, Money lineTotal) {

        public PricedLineView {
            Objects.requireNonNull(productId, "PricedLineView productId must not be null");
            Objects.requireNonNull(quantity, "PricedLineView quantity must not be null");
            Objects.requireNonNull(unitFinalPrice, "PricedLineView unitFinalPrice must not be null");
            Objects.requireNonNull(lineTotal, "PricedLineView lineTotal must not be null");
        }
    }

    /**
     * Unchecked, nested exception owned by this façade — never
     * {@code pricing.domain.exception.ProductNotAvailableException}, which a cross-context caller
     * must not import (ADR-0003 §Decision item 2).
     */
    class ProductUnavailableException extends RuntimeException {
        public ProductUnavailableException(String message) {
            super(message);
        }
    }
}

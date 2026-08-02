package com.ecommerce.checkout.infrastructure.adapter;

import com.ecommerce.checkout.domain.exception.ProductUnavailableForCheckoutException;
import com.ecommerce.checkout.domain.model.RecalculatedLine;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.port.out.PriceQuotePort;
import com.ecommerce.pricing.application.port.PriceCalculationPort;
import com.ecommerce.pricing.application.port.PriceCalculationPort.PriceLineRequest;
import com.ecommerce.pricing.application.port.PriceCalculationPort.PriceQuote;
import com.ecommerce.pricing.application.port.PriceCalculationPort.PricedLineView;

import java.util.List;
import java.util.Objects;

/**
 * Implements checkout's {@link PriceQuotePort} by delegating to pricing's public
 * {@link PriceCalculationPort} — the only dependency this adapter (and, transitively, the
 * checkout context) has on the pricing context (ADR-0003 §Decision item 2).
 *
 * <p>Translates pricing's {@link PriceQuote} into checkout's own {@link RecalculatedTotal} here —
 * checkout's application/domain layers never see a pricing type, mirroring
 * {@code cart.infrastructure.adapter.ProductCatalogAdapter} translating catalog's
 * {@code ProductSummary} into cart's own {@code CatalogProductView}. Also translates pricing's
 * façade-owned {@link PriceCalculationPort.ProductUnavailableException} into checkout's own
 * {@link ProductUnavailableForCheckoutException}.
 */
public class PriceQuoteAdapter implements PriceQuotePort {

    private final PriceCalculationPort priceCalculationPort;

    public PriceQuoteAdapter(PriceCalculationPort priceCalculationPort) {
        this.priceCalculationPort = Objects.requireNonNull(priceCalculationPort);
    }

    @Override
    public RecalculatedTotal recalculate(List<PriceQuoteLine> lines) {
        List<PriceLineRequest> requests = lines.stream()
                .map(l -> new PriceLineRequest(l.productId(), l.quantity()))
                .toList();

        PriceQuote quote;
        try {
            quote = priceCalculationPort.calculate(requests);
        } catch (PriceCalculationPort.ProductUnavailableException e) {
            throw new ProductUnavailableForCheckoutException(e.getMessage());
        }

        List<RecalculatedLine> recalculatedLines = quote.lines().stream().map(this::toRecalculatedLine).toList();

        return new RecalculatedTotal(recalculatedLines, quote.subtotal(), quote.discountTotal(),
                quote.shippingFee(), quote.grandTotal());
    }

    private RecalculatedLine toRecalculatedLine(PricedLineView line) {
        return new RecalculatedLine(line.productId(), line.quantity(), line.unitFinalPrice(), line.lineTotal());
    }
}

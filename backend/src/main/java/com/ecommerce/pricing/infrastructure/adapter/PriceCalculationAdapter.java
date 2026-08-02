package com.ecommerce.pricing.infrastructure.adapter;

import com.ecommerce.pricing.application.port.PriceCalculationPort;
import com.ecommerce.pricing.application.usecase.CalculateEffectivePriceUseCase;
import com.ecommerce.pricing.application.usecase.CalculateEffectivePriceUseCase.CalculatePriceCommand;
import com.ecommerce.pricing.domain.exception.ProductNotAvailableException;
import com.ecommerce.pricing.domain.model.PriceBreakdown;
import com.ecommerce.pricing.domain.model.PricedLine;

import java.util.List;
import java.util.Objects;

/**
 * Implements pricing's {@link PriceCalculationPort} by delegating to
 * {@link CalculateEffectivePriceUseCase} — the only dependency this adapter (and, transitively,
 * any cross-context caller through this façade) has on pricing's application/domain internals.
 * Mirrors {@code cart.infrastructure.adapter.ProductCatalogAdapter}.
 *
 * <p>Translates pricing's own {@link ProductNotAvailableException} (a
 * {@code BusinessRuleViolationException} leaf, private to pricing's domain package) into this
 * façade's nested {@link PriceCalculationPort.ProductUnavailableException} — a cross-context
 * caller (checkout) must never catch or import a pricing {@code domain.exception} type
 * (ADR-0003 §Decision item 2).
 */
public class PriceCalculationAdapter implements PriceCalculationPort {

    private final CalculateEffectivePriceUseCase calculateEffectivePriceUseCase;

    public PriceCalculationAdapter(CalculateEffectivePriceUseCase calculateEffectivePriceUseCase) {
        this.calculateEffectivePriceUseCase = Objects.requireNonNull(calculateEffectivePriceUseCase);
    }

    @Override
    public PriceQuote calculate(List<PriceLineRequest> lines) {
        List<CalculateEffectivePriceUseCase.PriceLineRequest> resolvedLines = lines.stream()
                .map(l -> new CalculateEffectivePriceUseCase.PriceLineRequest(l.productId(), l.quantity()))
                .toList();

        PriceBreakdown breakdown;
        try {
            breakdown = calculateEffectivePriceUseCase.execute(new CalculatePriceCommand(resolvedLines));
        } catch (ProductNotAvailableException e) {
            throw new ProductUnavailableException(e.getMessage());
        }

        List<PricedLineView> lineViews = breakdown.lines().stream().map(this::toLineView).toList();

        return new PriceQuote(lineViews, breakdown.subtotal(), breakdown.discountTotal(),
                breakdown.shippingFee(), breakdown.grandTotal().amount());
    }

    private PricedLineView toLineView(PricedLine line) {
        return new PricedLineView(line.productId(), line.quantity(), line.unitFinalPrice(), line.lineTotal());
    }
}

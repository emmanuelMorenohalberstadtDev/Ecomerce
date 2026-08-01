package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.util.Objects;

/**
 * One priced line within a {@link PriceBreakdown}: the resolved catalog base price and promotion
 * discount for a single {@code (productId, quantity)} pair, plus the computed unit-final price
 * (base minus discount) and line total (unit-final times quantity).
 *
 * <p>Pricing does not dedupe duplicate {@code productId} entries across a request's lines — see
 * {@code CalculateEffectivePriceUseCase} javadoc — so a single {@link PriceBreakdown} may contain
 * more than one {@code PricedLine} for the same product.
 */
public record PricedLine(
        ProductId productId,
        Quantity quantity,
        Money unitBasePrice,
        Money unitDiscount,
        Money unitFinalPrice,
        Money lineTotal) {

    public PricedLine {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unitBasePrice, "unitBasePrice must not be null");
        Objects.requireNonNull(unitDiscount, "unitDiscount must not be null");
        Objects.requireNonNull(unitFinalPrice, "unitFinalPrice must not be null");
        Objects.requireNonNull(lineTotal, "lineTotal must not be null");
    }
}

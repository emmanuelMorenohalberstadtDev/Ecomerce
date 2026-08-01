package com.ecommerce.pricing.domain.model;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain service composing a {@link PriceBreakdown} from already-resolved price lines and a
 * flat shipping fee.
 *
 * <p>Zero I/O, zero Spring/JPA/Jackson dependencies, zero outbound port references. Every input
 * (base price, discount) has already been resolved by the caller
 * ({@code CalculateEffectivePriceUseCase}, which performs both port calls before invoking this
 * class), so {@link #calculate} is a deterministic function of its arguments and is fully
 * unit-testable with zero mocks.
 *
 * <p><strong>Composition order</strong> (per line): {@code unitFinal = unitBasePrice - unitDiscount};
 * {@code lineTotal = unitFinal * quantity}. Aggregate: {@code subtotal = Σ lineTotal};
 * {@code discountTotal = Σ (unitDiscount * quantity)}; {@code grandTotal = subtotal + shippingFee}.
 * No tax step — catalog's base price is already treated as tax-inclusive in v1 (non-goal, not a
 * gap).
 *
 * <p><strong>Rounding:</strong> no manual rounding step anywhere in this class. {@link Money}'s
 * compact constructor and {@link Money#multiply(int)} already normalise to {@code HALF_UP}
 * scale-2 on every operation, so {@code Money} itself is the single rounding authority, applied
 * once per value at the point it is produced. This deliberately diverges from the {@code pricing}
 * skill's illustrative generic example (scale-4 / {@code HALF_EVEN} / a manual {@code applyRate}
 * step) — this codebase's shared-kernel {@code Money} is the actual rounding policy in force.
 */
public final class PriceCalculator {

    public PriceBreakdown calculate(List<LineInput> lines, Money shippingFee) {
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(shippingFee, "shippingFee must not be null");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }

        List<PricedLine> pricedLines = new ArrayList<>(lines.size());
        Money subtotal = Money.zero(shippingFee.currency());
        Money discountTotal = Money.zero(shippingFee.currency());

        for (LineInput line : lines) {
            Money unitFinal = line.unitBasePrice().subtract(line.unitDiscount());
            Money lineTotal = unitFinal.multiply(line.quantity().value());
            Money lineDiscount = line.unitDiscount().multiply(line.quantity().value());

            pricedLines.add(new PricedLine(
                    line.productId(), line.quantity(), line.unitBasePrice(), line.unitDiscount(),
                    unitFinal, lineTotal));

            subtotal = subtotal.add(lineTotal);
            discountTotal = discountTotal.add(lineDiscount);
        }

        Money grandTotalAmount = subtotal.add(shippingFee);

        return new PriceBreakdown(
                List.copyOf(pricedLines), subtotal, discountTotal, shippingFee,
                new EffectivePrice(grandTotalAmount));
    }

    /**
     * Fully resolved inputs for one price line — both port calls (catalog lookup, promotion
     * lookup) have already happened before this is constructed; {@link PriceCalculator} performs
     * no I/O on it.
     */
    public record LineInput(ProductId productId, Quantity quantity, Money unitBasePrice, Money unitDiscount) {

        public LineInput {
            Objects.requireNonNull(productId, "productId must not be null");
            Objects.requireNonNull(quantity, "quantity must not be null");
            Objects.requireNonNull(unitBasePrice, "unitBasePrice must not be null");
            Objects.requireNonNull(unitDiscount, "unitDiscount must not be null");
        }
    }
}

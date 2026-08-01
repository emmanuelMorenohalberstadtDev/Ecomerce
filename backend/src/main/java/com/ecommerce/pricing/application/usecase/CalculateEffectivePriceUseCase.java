package com.ecommerce.pricing.application.usecase;

import com.ecommerce.pricing.domain.exception.ProductNotAvailableException;
import com.ecommerce.pricing.domain.model.PriceBreakdown;
import com.ecommerce.pricing.domain.model.PriceCalculator;
import com.ecommerce.pricing.domain.model.PriceCalculator.LineInput;
import com.ecommerce.pricing.domain.port.out.ProductCatalogPort;
import com.ecommerce.pricing.domain.port.out.ProductCatalogPort.PricedProductView;
import com.ecommerce.pricing.domain.port.out.PromotionPolicyPort;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes the effective price for a set of {@code (productId, quantity)} lines: catalog base
 * price, per-unit promotion discount (stubbed at zero in v1 — the real {@code promotions} context
 * does not exist yet), and a flat shipping fee.
 *
 * <p>Plain constructor-injected class — no {@code @Service}/{@code @Transactional}. There is
 * nothing to transact: this is a pure read-and-compute over catalog data, with no writes and no
 * persistence of its own (pricing owns no database tables).
 *
 * <p><strong>No currency parameter:</strong> currency comes from the {@link Money} each catalog
 * lookup returns (single-currency v1). Before handing resolved lines to {@link PriceCalculator},
 * this use case defensively verifies every line — and the shipping fee — share one currency,
 * failing fast with a clear message rather than letting {@link Money#add}'s generic
 * currency-mismatch exception surface confusingly mid-composition.
 *
 * <p><strong>No shipping parameter:</strong> the flat shipping fee is a policy constant, resolved
 * from Spring config in {@code infrastructure.config.PricingInfrastructureConfiguration} and
 * handed into this class's constructor as an already-built {@link Money} value — required so that
 * {@code application} never depends on {@code infrastructure} (ArchitectureTest rule 4).
 *
 * <p><strong>No deduplication:</strong> this use case prices exactly the lines it is given; it
 * does not merge or dedupe repeated {@code productId} entries in the input list. The intended
 * future caller, {@code checkout}, derives its lines from cart's already-one-line-per-product
 * {@code CartLine}s, so deduplication is not this class's responsibility.
 */
public class CalculateEffectivePriceUseCase {

    private final ProductCatalogPort productCatalogPort;
    private final PromotionPolicyPort promotionPolicyPort;
    private final PriceCalculator priceCalculator;
    private final Money shippingFee;

    public CalculateEffectivePriceUseCase(ProductCatalogPort productCatalogPort,
                                           PromotionPolicyPort promotionPolicyPort,
                                           PriceCalculator priceCalculator,
                                           Money shippingFee) {
        this.productCatalogPort = Objects.requireNonNull(productCatalogPort);
        this.promotionPolicyPort = Objects.requireNonNull(promotionPolicyPort);
        this.priceCalculator = Objects.requireNonNull(priceCalculator);
        this.shippingFee = Objects.requireNonNull(shippingFee);
    }

    public PriceBreakdown execute(CalculatePriceCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        List<LineInput> resolvedLines = new ArrayList<>(command.lines().size());
        for (PriceLineRequest line : command.lines()) {
            PricedProductView product = productCatalogPort.findActiveById(line.productId())
                    .orElseThrow(() -> new ProductNotAvailableException(
                            "Product not available: " + line.productId()));

            Money unitDiscount = promotionPolicyPort.discountForUnit(line.productId(), product.basePrice());
            resolvedLines.add(new LineInput(line.productId(), line.quantity(), product.basePrice(), unitDiscount));
        }

        requireSingleCurrency(resolvedLines);

        return priceCalculator.calculate(resolvedLines, shippingFee);
    }

    /**
     * Fails fast, with a clear message naming the offending product, if any resolved line's
     * currency disagrees with the configured shipping fee's currency — rather than letting a
     * generic {@code Money.add} currency-mismatch exception surface confusingly deep inside
     * {@link PriceCalculator}'s aggregation loop.
     */
    private void requireSingleCurrency(List<LineInput> resolvedLines) {
        String expectedCurrency = shippingFee.currency();
        for (LineInput line : resolvedLines) {
            String lineCurrency = line.unitBasePrice().currency();
            if (!lineCurrency.equals(expectedCurrency)) {
                throw new IllegalStateException(
                        "Currency mismatch while composing price: shipping fee is in "
                                + expectedCurrency + " but product " + line.productId()
                                + " resolved a base price in " + lineCurrency
                                + " — pricing v1 supports a single currency only");
            }
        }
    }

    public record CalculatePriceCommand(List<PriceLineRequest> lines) {

        public CalculatePriceCommand {
            Objects.requireNonNull(lines, "lines must not be null");
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("lines must not be empty");
            }
            lines = List.copyOf(lines);
        }
    }

    public record PriceLineRequest(ProductId productId, Quantity quantity) {

        public PriceLineRequest {
            Objects.requireNonNull(productId, "productId must not be null");
            Objects.requireNonNull(quantity, "quantity must not be null");
        }
    }
}

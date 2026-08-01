package com.ecommerce.pricing.domain.port.out;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

/**
 * Pricing's own outbound port for resolving the per-unit promotion discount to apply to a
 * product's base price.
 *
 * <p>The real {@code promotions} bounded context does not exist yet. v1 is served by
 * {@code pricing.infrastructure.adapter.NoDiscountPromotionPolicyAdapter}, which unconditionally
 * returns zero. This port exists now so {@link PriceCalculator}'s composition order already
 * reserves the item-level-discount step; swapping in a real promotions adapter later is a wiring
 * change only, with no change to the use case or the domain service.
 */
public interface PromotionPolicyPort {

    /**
     * @param productId     the product the discount applies to
     * @param unitBasePrice the product's current catalog base price (the result must share its
     *                      currency)
     * @return the discount to subtract from one unit's base price; {@link Money#zero} when no
     *         promotion applies
     */
    Money discountForUnit(ProductId productId, Money unitBasePrice);
}

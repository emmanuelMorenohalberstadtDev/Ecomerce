package com.ecommerce.pricing.infrastructure.adapter;

import com.ecommerce.pricing.domain.port.out.PromotionPolicyPort;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.util.Objects;

/**
 * v1 stub implementation of {@link PromotionPolicyPort}: the real {@code promotions} bounded
 * context does not exist yet, so every product's per-unit discount is unconditionally zero.
 *
 * <p>Swapping in a real promotions adapter later is a wiring change only —
 * {@code PriceCalculator}'s composition order already reserves the item-level-discount step, so
 * neither the use case nor the domain service needs to change.
 */
public class NoDiscountPromotionPolicyAdapter implements PromotionPolicyPort {

    @Override
    public Money discountForUnit(ProductId productId, Money unitBasePrice) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(unitBasePrice, "unitBasePrice must not be null");
        return Money.zero(unitBasePrice.currency());
    }
}

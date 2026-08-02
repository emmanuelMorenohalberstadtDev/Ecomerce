package com.ecommerce.checkout.domain.port.out;

import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Checkout's own outbound port representing what it needs from the cart context — deliberately
 * checkout-shaped, not a re-export of cart's {@code CartReadPort}
 * ({@code CartReadPort.CartSnapshot}), so checkout never depends on a cart application-layer wire
 * type and the two contexts can evolve their shapes independently (ADR-0003 §Decision item 2,
 * mirroring {@code cart.domain.port.out.ProductCatalogPort}'s relationship to catalog's
 * {@code ProductLookupPort} exactly).
 *
 * <p>Implemented by {@code checkout.infrastructure.adapter.CartLinesAdapter}, whose only
 * dependency on cart is the cart-owned {@code com.ecommerce.cart.application.port.CartReadPort}
 * bean — the sanctioned crossing point (application.port).
 */
public interface CartLinesPort {

    /**
     * @param customerId the owning customer
     * @return the customer's active cart lines, or {@link Optional#empty()} if the customer has
     *         no active cart
     */
    Optional<CartSnapshot> findActiveCartLines(CustomerId customerId);

    /** Minimal projection of a cart, shaped for checkout's own needs. */
    record CartSnapshot(CartId cartId, List<CartLineView> lines) {

        public CartSnapshot {
            Objects.requireNonNull(cartId, "CartSnapshot cartId must not be null");
            Objects.requireNonNull(lines, "CartSnapshot lines must not be null");
            lines = List.copyOf(lines);
        }
    }

    /** One line's product and quantity. */
    record CartLineView(ProductId productId, Quantity quantity) {

        public CartLineView {
            Objects.requireNonNull(productId, "CartLineView productId must not be null");
            Objects.requireNonNull(quantity, "CartLineView quantity must not be null");
        }
    }
}

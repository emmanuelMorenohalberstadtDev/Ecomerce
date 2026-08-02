package com.ecommerce.cart.application.port;

import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound-facing, read-only public façade cart exposes to other bounded contexts that need to
 * read a customer's cart lines without depending on cart's internal domain model.
 *
 * <p><strong>Why this exists:</strong> the checkout context needs to read a customer's active
 * cart lines to price and reserve stock for them (ADR-0003 §Decision item 1) — cart's domain
 * layer ({@code Cart}, {@code CartRepository}) is private to cart's own use cases. Mirrors
 * {@code catalog.application.port.ProductLookupPort} exactly: a small, deliberate, versioned
 * public contract living in {@code application.port} — the one package other bounded contexts are
 * allowed to depend on ({@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}).
 *
 * <p><strong>Customer-only, by design:</strong> unlike cart's own internal lookups (which also
 * serve guest carts by token hash), this façade only ever resolves by {@link CustomerId} —
 * checkout has no guest path (security-architecture §3.4: checkout GUEST access is 401).
 *
 * <p>Returns {@link CartSnapshot} — a small, read-only projection (cart id + line
 * productId/quantity pairs) — never the full {@code Cart} aggregate (no snapshot pricing, no
 * unavailable flags), so cart's internal domain shape is free to change without breaking callers
 * across the context boundary. Pricing these lines is checkout's job via its own
 * {@code PriceQuotePort}, not cart's.
 */
public interface CartReadPort {

    /**
     * @param customerId the owning customer
     * @return the customer's ACTIVE cart lines, or {@link Optional#empty()} if the customer has
     *         no ACTIVE cart
     */
    Optional<CartSnapshot> findActiveCartByCustomer(CustomerId customerId);

    /** Minimal, read-only projection of a cart for cross-context consumers. */
    record CartSnapshot(CartId cartId, List<CartLineView> lines) {

        public CartSnapshot {
            Objects.requireNonNull(cartId, "CartSnapshot cartId must not be null");
            Objects.requireNonNull(lines, "CartSnapshot lines must not be null");
            lines = List.copyOf(lines);
        }
    }

    /** One line's product and quantity — no snapshot price, no unavailability flag. */
    record CartLineView(ProductId productId, Quantity quantity) {

        public CartLineView {
            Objects.requireNonNull(productId, "CartLineView productId must not be null");
            Objects.requireNonNull(quantity, "CartLineView quantity must not be null");
        }
    }
}

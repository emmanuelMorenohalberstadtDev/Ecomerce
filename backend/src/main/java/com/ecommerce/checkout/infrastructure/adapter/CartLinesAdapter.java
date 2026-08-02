package com.ecommerce.checkout.infrastructure.adapter;

import com.ecommerce.cart.application.port.CartReadPort;
import com.ecommerce.checkout.domain.port.out.CartLinesPort;
import com.ecommerce.shared.id.CustomerId;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements checkout's {@link CartLinesPort} by delegating to cart's public
 * {@link CartReadPort} — the only dependency this adapter (and, transitively, the checkout
 * context) has on the cart context, satisfying
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events} both in letter
 * ({@code application.port} is not a forbidden target package) and in spirit (ADR-0003
 * §Decision item 2: checkout's domain/application layers never see a cart type directly — this
 * adapter is the one place the translation happens).
 */
public class CartLinesAdapter implements CartLinesPort {

    private final CartReadPort cartReadPort;

    public CartLinesAdapter(CartReadPort cartReadPort) {
        this.cartReadPort = Objects.requireNonNull(cartReadPort);
    }

    @Override
    public Optional<CartSnapshot> findActiveCartLines(CustomerId customerId) {
        return cartReadPort.findActiveCartByCustomer(customerId)
                .map(snapshot -> new CartSnapshot(
                        snapshot.cartId(),
                        snapshot.lines().stream()
                                .map(line -> new CartLineView(line.productId(), line.quantity()))
                                .toList()));
    }
}

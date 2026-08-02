package com.ecommerce.cart.infrastructure.adapter;

import com.ecommerce.cart.application.port.CartReadPort;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.shared.id.CustomerId;

import java.util.Objects;
import java.util.Optional;

/**
 * Thin translation adapter implementing {@link CartReadPort} on top of cart's existing
 * {@link CartRepository} domain port — no new business logic, just a projection from the full
 * {@link Cart} aggregate to the small {@link CartReadPort.CartSnapshot} shape other contexts are
 * allowed to see. Mirrors {@code catalog.infrastructure.adapter.ProductLookupAdapter}.
 *
 * <p>Wired as an explicit {@code @Bean} in {@code CartInfrastructureConfiguration} (backend-
 * architecture §6 wiring convention).
 */
public class CartReadAdapter implements CartReadPort {

    private final CartRepository cartRepository;

    public CartReadAdapter(CartRepository cartRepository) {
        this.cartRepository = Objects.requireNonNull(cartRepository);
    }

    @Override
    public Optional<CartSnapshot> findActiveCartByCustomer(CustomerId customerId) {
        return cartRepository.findActiveByCustomerId(customerId)
                .map(cart -> new CartSnapshot(
                        cart.getId(),
                        cart.getLines().stream()
                                .map(line -> new CartLineView(line.productId(), line.quantity()))
                                .toList()));
    }
}

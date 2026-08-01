package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Removes a line entirely from the caller's cart.
 *
 * <p>Operates on an existing cart only — never auto-creates
 * ({@link com.ecommerce.cart.domain.exception.CartNotFoundException}, 404, if the caller has no
 * ACTIVE cart; {@link com.ecommerce.cart.domain.exception.CartLineNotFoundException}, 404, if
 * the product is not a line in it).
 *
 * <p>Auth decision table row: GUEST — 200 (own cart); CUSTOMER — 200 (own cart); ADMIN — 403
 * ({@link PreAuthorize}).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class RemoveLineUseCase {

    private final CartRepository cartRepository;

    public RemoveLineUseCase(CartRepository cartRepository) {
        this.cartRepository = Objects.requireNonNull(cartRepository);
    }

    @PreAuthorize("!hasRole('ADMIN')")
    public Cart execute(RemoveLineCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Cart cart = CartLookup.resolveActiveOwnCart(cartRepository, command.customerId(), command.rawGuestToken());
        cart.removeLine(command.productId());
        return cartRepository.save(cart);
    }

    public record RemoveLineCommand(CustomerId customerId, String rawGuestToken, ProductId productId) {}
}

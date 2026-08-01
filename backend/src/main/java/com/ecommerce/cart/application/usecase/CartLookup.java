package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.exception.CartNotFoundException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.security.GuestTokenHasher;
import com.ecommerce.shared.id.CustomerId;

import java.util.Optional;

/**
 * Package-private helper shared by the use cases that operate on an <em>existing</em> cart
 * ({@link ViewCartUseCase}, {@link UpdateLineQuantityUseCase}, {@link RemoveLineUseCase}) —
 * centralises "resolve the caller's own ACTIVE cart, hashing the guest token if present" so the
 * raw-token-to-hash translation happens in exactly one place. Not a public API, not a god
 * utility — one job, package-scoped.
 */
final class CartLookup {

    private CartLookup() {}

    /** Returns the SHA-256 hash of {@code rawGuestToken}, or {@code null} if it is null/blank. */
    static String hashOrNull(String rawGuestToken) {
        return (rawGuestToken == null || rawGuestToken.isBlank()) ? null : GuestTokenHasher.sha256Hex(rawGuestToken);
    }

    /**
     * Resolves the caller's own ACTIVE cart by customer id (preferred, when authenticated) or
     * guest token (hashed here).
     *
     * @throws CartNotFoundException if neither identity resolves to an ACTIVE cart
     */
    static Cart resolveActiveOwnCart(CartRepository cartRepository, CustomerId customerId, String rawGuestToken) {
        Optional<Cart> found;
        if (customerId != null) {
            found = cartRepository.findActiveByCustomerId(customerId);
        } else {
            String hash = hashOrNull(rawGuestToken);
            found = hash == null ? Optional.empty() : cartRepository.findActiveByGuestTokenHash(hash);
        }
        return found.orElseThrow(() -> new CartNotFoundException("No active cart found for the current identity"));
    }
}

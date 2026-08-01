package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer;
import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer.IssuedGuestToken;
import com.ecommerce.shared.id.CustomerId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the caller's current ACTIVE cart, creating one if none exists yet.
 *
 * <p>Two identity paths:
 * <ul>
 *   <li><strong>Customer</strong> ({@code customerId != null}): find-or-create the customer's
 *       ACTIVE cart. Never mints a guest token.</li>
 *   <li><strong>Guest</strong> ({@code customerId == null}): if {@code rawGuestToken} resolves
 *       (hashed) to an existing ACTIVE guest cart, return it. Otherwise mint a brand-new guest
 *       identity via {@link GuestCartTokenIssuer} and create a cart for it — this covers both
 *       "no cookie yet" and "cookie present but stale/unknown" (e.g. an expired or already-merged
 *       token), the same way a stale session simply gets a new one rather than an error.</li>
 * </ul>
 *
 * <p>Auth decision table row: GUEST — 200/201 (own cart, own or new token); CUSTOMER — 200/201
 * (own cart); ADMIN — 403 ({@link PreAuthorize}, security-architecture §3.4: "admins don't
 * shop").
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class GetOrCreateCartUseCase {

    private final CartRepository cartRepository;
    private final GuestCartTokenIssuer guestCartTokenIssuer;

    public GetOrCreateCartUseCase(CartRepository cartRepository, GuestCartTokenIssuer guestCartTokenIssuer) {
        this.cartRepository = Objects.requireNonNull(cartRepository);
        this.guestCartTokenIssuer = Objects.requireNonNull(guestCartTokenIssuer);
    }

    @PreAuthorize("!hasRole('ADMIN')")
    public GetOrCreateCartResult execute(CustomerId customerId, String rawGuestToken) {
        if (customerId != null) {
            Optional<Cart> existing = cartRepository.findActiveByCustomerId(customerId);
            if (existing.isPresent()) {
                return new GetOrCreateCartResult(existing.get(), false, null);
            }
            Cart saved = cartRepository.save(Cart.createCustomerCart(customerId));
            return new GetOrCreateCartResult(saved, true, null);
        }

        String hash = CartLookup.hashOrNull(rawGuestToken);
        if (hash != null) {
            Optional<Cart> existing = cartRepository.findActiveByGuestTokenHash(hash);
            if (existing.isPresent()) {
                return new GetOrCreateCartResult(existing.get(), false, null);
            }
        }

        IssuedGuestToken issued = guestCartTokenIssuer.issue();
        Cart saved = cartRepository.save(Cart.createGuestCart(issued.tokenHash()));
        return new GetOrCreateCartResult(saved, true, issued.rawToken());
    }

    /**
     * @param cart            the caller's ACTIVE cart, existing or freshly created
     * @param created         {@code true} if a new cart row was created by this call
     * @param newRawGuestToken non-null only when a brand-new guest identity was minted — the
     *                         caller (controller) must set this as the guest cart cookie
     */
    public record GetOrCreateCartResult(Cart cart, boolean created, String newRawGuestToken) {}
}

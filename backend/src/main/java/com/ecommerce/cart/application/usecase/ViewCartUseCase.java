package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.CartLine;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort;
import com.ecommerce.shared.id.CustomerId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Returns the caller's ACTIVE cart, with each line re-checked against catalog so a line whose
 * product has since been retired is flagged {@code unavailable} (rule 10).
 *
 * <p><strong>Persistence choice — compute on read, do not persist:</strong> the
 * {@code cart_items.unavailable} column exists (V003) but this use case never calls
 * {@code CartRepository.save} after flagging. Rationale, per the migration's own column comment:
 * "never drives a CHECK ... re-validated at checkout regardless of this flag's freshness" — the
 * flag is a display hint only, so recomputing it on every read is simpler and cannot drift stale
 * between reads, at the cost of one extra catalog lookup per line per view. If this read path
 * ever needs to scale past that cost, persisting on flip (only when the flag actually changes)
 * is the natural next step — not implemented here to keep v1 simple, per the task's own guidance
 * to pick the simpler approach.
 *
 * <p>Never auto-creates a cart — {@link com.ecommerce.cart.domain.exception.CartNotFoundException}
 * (404) if the caller has no ACTIVE cart. GET is safe and body-less at the HTTP layer
 * (api-guidelines §1.9); creating a cart as a side effect of a read would violate that.
 *
 * <p>Auth decision table row: GUEST — 200/404 (own cart); CUSTOMER — 200/404 (own cart); ADMIN —
 * 403 ({@link PreAuthorize}).
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class ViewCartUseCase {

    private final CartRepository cartRepository;
    private final ProductCatalogPort productCatalogPort;

    public ViewCartUseCase(CartRepository cartRepository, ProductCatalogPort productCatalogPort) {
        this.cartRepository = Objects.requireNonNull(cartRepository);
        this.productCatalogPort = Objects.requireNonNull(productCatalogPort);
    }

    @PreAuthorize("!hasRole('ADMIN')")
    public Cart execute(CustomerId customerId, String rawGuestToken) {
        Cart cart = CartLookup.resolveActiveOwnCart(cartRepository, customerId, rawGuestToken);

        // Iterates a defensive copy (Cart#getLines) while markLineUnavailable mutates the live
        // internal list — safe, no ConcurrentModificationException.
        for (CartLine line : cart.getLines()) {
            boolean stillActive = productCatalogPort.findActiveProduct(line.productId()).isPresent();
            if (!stillActive) {
                cart.markLineUnavailable(line.productId());
            }
        }
        return cart;
    }
}

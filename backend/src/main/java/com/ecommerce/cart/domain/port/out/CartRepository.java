package com.ecommerce.cart.domain.port.out;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;

import java.util.Optional;

/**
 * Domain port (outbound) for {@link Cart} persistence.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaCartRepository} in the infrastructure
 * layer. One repository per aggregate root (domain-model.md §2 ownership rules) — no separate
 * repository for {@code CartLine}, persisted as part of the {@code Cart} aggregate.
 *
 * <p>{@link #findActiveByCustomerId} and {@link #findActiveByGuestTokenHash} mirror the two
 * partial-unique-index point lookups in {@code V003__create_cart_schema.sql}
 * ({@code uq_carts_customer_active}, {@code uq_carts_guest_token_active}) — each also doubles as
 * the enforcement point for "at most one ACTIVE cart per identity" (database-design.md §3.1).
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and
 * {@code repository_interfaces_live_in_domain}.
 */
public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findById(CartId id);

    Optional<Cart> findActiveByCustomerId(CustomerId customerId);

    /** @param guestTokenHash the SHA-256 hash of the guest cart token — never a raw token value */
    Optional<Cart> findActiveByGuestTokenHash(String guestTokenHash);
}

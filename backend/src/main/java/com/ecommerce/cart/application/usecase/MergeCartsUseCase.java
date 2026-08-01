package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.shared.id.CustomerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Merges a guest cart into a newly-authenticated customer's cart (rule 1).
 *
 * <p><strong>How this gets triggered — the design fork, resolved:</strong> domain-model.md §4
 * documents cart's {@code MergeCartsUseCase} as the consumer of {@code UserAuthenticatedEvent},
 * but that event carries only {@code UserId} + {@code occurredAt} (no guest-token correlator),
 * and it is published from {@code auth.application.usecase.LoginUseCase}, which has no access to
 * the HTTP guest-cart cookie (that's a presentation-layer concern; {@code AuthController} is the
 * only place in a login request that holds both the freshly-authenticated identity and the
 * guest-cart cookie). Since {@code UserAuthenticatedEvent} and {@code AuthController} are both
 * read-only reference files this change is not permitted to edit, this use case is instead
 * invoked <strong>synchronously and opportunistically from {@code CartController}</strong>: on
 * any cart endpoint, if the caller is authenticated (JWT resolves a {@code CustomerId}) AND a
 * guest-cart cookie is still present, the controller calls this use case before the main
 * operation, then clears the cookie. This covers the real functional requirement (a guest cart
 * is folded into the customer cart no later than the customer's first cart interaction after
 * login) without editing any auth file. {@code CartMergeEventListener} still subscribes to
 * {@code UserAuthenticatedEvent} for the documented consumer wiring, but — lacking a guest-token
 * correlator — cannot itself perform a merge; see its javadoc. Flagged to the orchestrator as an
 * open question: an instant-at-login merge would need one additive line in
 * {@code AuthController.login()} to call this use case (or publish a richer event), which is
 * outside this change's file-editing permissions.
 *
 * <p>Two cases:
 * <ul>
 *   <li>No existing ACTIVE customer cart: nothing to merge — the guest cart is claimed directly
 *       ({@link Cart#claimForCustomer}).</li>
 *   <li>An ACTIVE customer cart exists: {@link Cart#mergeFrom} folds the guest cart's lines in;
 *       both carts are saved (the guest cart transitions to MERGED).</li>
 * </ul>
 * Idempotent/safe to call with no guest cart present (already merged, expired, or never
 * existed) — a no-op.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class MergeCartsUseCase {

    private static final Logger log = LoggerFactory.getLogger(MergeCartsUseCase.class);

    private final CartRepository cartRepository;

    public MergeCartsUseCase(CartRepository cartRepository) {
        this.cartRepository = Objects.requireNonNull(cartRepository);
    }

    @PreAuthorize("!hasRole('ADMIN')")
    public void execute(CustomerId customerId, String rawGuestToken) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        String guestTokenHash = CartLookup.hashOrNull(rawGuestToken);
        if (guestTokenHash == null) {
            return;
        }

        Optional<Cart> maybeGuestCart = cartRepository.findActiveByGuestTokenHash(guestTokenHash);
        if (maybeGuestCart.isEmpty()) {
            return;
        }
        Cart guestCart = maybeGuestCart.get();

        Optional<Cart> maybeCustomerCart = cartRepository.findActiveByCustomerId(customerId);
        if (maybeCustomerCart.isEmpty()) {
            guestCart.claimForCustomer(customerId);
            cartRepository.save(guestCart);
            log.info("Guest cart {} claimed directly by customer {} (no prior customer cart)",
                    guestCart.getId(), customerId);
            return;
        }

        Cart customerCart = maybeCustomerCart.get();
        customerCart.mergeFrom(guestCart);
        cartRepository.save(customerCart);
        cartRepository.save(guestCart);
        log.info("Guest cart {} merged into customer cart {} for customer {}",
                guestCart.getId(), customerCart.getId(), customerId);
    }
}

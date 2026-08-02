package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.exception.CheckoutSessionNotFoundException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Returns a single checkout session, ownership-scoped to the caller
 * (security-architecture §3.2/§3.3: "the repository query scopes by owner", "ownership failures
 * are 404, never 403").
 *
 * <p>Not named in ADR-0003's in-scope use-case list (which covers the three mutating flows —
 * {@code StartCheckoutUseCase}, {@code ConfirmRecalculatedTotalUseCase},
 * {@code ExpirePaymentWindowUseCase}) but required to serve the brief's mandated
 * {@code GET /api/v1/checkout-sessions/{id}} endpoint — a small, uncontroversial read-only
 * addition, mirroring {@code cart.application.usecase.ViewCartUseCase}/
 * {@code catalog.application.usecase.GetProductUseCase} exactly.
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class GetCheckoutSessionUseCase {

    private final CheckoutSessionRepository checkoutSessionRepository;

    public GetCheckoutSessionUseCase(CheckoutSessionRepository checkoutSessionRepository) {
        this.checkoutSessionRepository = Objects.requireNonNull(checkoutSessionRepository);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public CheckoutSession execute(CheckoutSessionId checkoutSessionId, CustomerId customerId) {
        return checkoutSessionRepository.findByIdAndCustomerId(checkoutSessionId, customerId)
                .orElseThrow(() -> new CheckoutSessionNotFoundException(
                        "No checkout session " + checkoutSessionId + " for the current customer"));
    }
}

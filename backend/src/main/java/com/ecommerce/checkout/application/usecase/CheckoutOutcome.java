package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.model.CheckoutSession;

import java.util.Objects;

/**
 * Result of {@link StartCheckoutUseCase#execute}/{@link ConfirmRecalculatedTotalUseCase#execute}
 * — an expected alternate outcome (backend-architecture §5: "expected alternate outcomes are
 * results, not exceptions ... exceptions mark rule violations"), not a thrown exception, because
 * a price mismatch requiring customer re-confirmation is a normal step of the checkout flow, not
 * a rule violation. See {@link StartCheckoutUseCase}'s javadoc for the full reasoning and the
 * {@code price-changed} problem-type trade-off this choice implies.
 */
public sealed interface CheckoutOutcome {

    CheckoutSession session();

    /** The session reached {@code AWAITING_PAYMENT}: stock reserved, payment window running. */
    record Confirmed(CheckoutSession session) implements CheckoutOutcome {
        public Confirmed {
            Objects.requireNonNull(session, "session must not be null");
        }
    }

    /**
     * The session is (still) {@code PENDING_CONFIRMATION}: the recalculated total differs from
     * what the customer expected/confirmed. {@link #session()} carries the newest total for the
     * caller to re-display.
     */
    record NeedsReconfirmation(CheckoutSession session) implements CheckoutOutcome {
        public NeedsReconfirmation {
            Objects.requireNonNull(session, "session must not be null");
        }
    }
}

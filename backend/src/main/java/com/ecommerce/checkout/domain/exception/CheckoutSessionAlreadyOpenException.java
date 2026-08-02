package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.ConflictException;

/**
 * Thrown when {@code StartCheckoutUseCase} is called by a customer who already has an open
 * checkout session ({@code PENDING_CONFIRMATION} or {@code AWAITING_PAYMENT}).
 *
 * <p><strong>Abuse case this closes (security-engineer review gate, HIGH):</strong> starting a
 * checkout session reserves real stock via {@link com.ecommerce.checkout.domain.port.out.ReservationPort}
 * but never locks or deactivates the source cart —
 * {@link com.ecommerce.checkout.domain.port.out.CartLinesPort#findActiveCartLines} is a read-only
 * lookup. Without this guard, a single authenticated CUSTOMER could repeatedly call
 * {@code POST /checkout-sessions} against the same active cart with a fresh
 * {@code Idempotency-Key} each time — the per-key idempotency check in {@code StartCheckoutUseCase}
 * would never trip, and each call would independently reserve stock for the full 15-minute payment
 * window. Against a scarce product this is a stock-lock / reservation-spam denial-of-service
 * against exactly the asset security-architecture.md §1.1 calls out ("Stock &amp; coupon caps ...
 * Oversell, cap-drain fraud"). This exception is thrown instead of allowing a second reservation
 * to be created for the same customer while one is already in flight.
 *
 * <p>Modeled as a conflict (409), matching {@link DuplicateIdempotencyKeyException}'s reasoning: the
 * request is well-formed, but the customer's current state (an already-open session) conflicts
 * with starting a new one.
 */
public final class CheckoutSessionAlreadyOpenException extends ConflictException {

    public CheckoutSessionAlreadyOpenException(String message) {
        super(message);
    }
}

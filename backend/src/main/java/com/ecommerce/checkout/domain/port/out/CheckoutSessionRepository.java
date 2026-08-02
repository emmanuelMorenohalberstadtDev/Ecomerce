package com.ecommerce.checkout.domain.port.out;

import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain port (outbound) for {@link CheckoutSession} persistence.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaCheckoutSessionRepository} in
 * infrastructure. One repository per aggregate root (domain-model.md §2 ownership rules).
 *
 * <p>Unlike {@code inventory.domain.port.out.StockReservationRepository}'s header-only
 * {@code updateStatus} (forced by {@code stock_reservation_lines}' insert-only grants),
 * {@link #save} persists the whole aggregate on every state transition: {@code checkout_sessions}
 * has no insert-only child table to protect (no per-line breakdown is persisted at all — see
 * {@code RecalculatedTotal}'s javadoc) and its status transitions are not concurrency-sensitive
 * the way stock's atomic decrement is. A plain {@code @Version}-guarded save is sufficient here
 * (ADR-0003 brief: "no 'last unit' style race on a checkout session") — {@code @Version} still
 * guards every {@link #save} against a lost concurrent update, surfaced as a 409.
 *
 * <p>{@link #findByIdAndCustomerId} is the ownership-scoped lookup
 * (security-architecture §3.2: "the repository query scopes by owner so unowned data is
 * unfetchable, not merely filtered"). {@link #findAwaitingPaymentExpiredAsOf} backs the payment
 * window sweep via {@code idx_checkout_sessions_deadline_open}. {@link #findByCustomerIdAndIdempotencyKey}
 * backs the per-customer idempotency-key replay check via {@code uq_checkout_sessions_idempotency}
 * (customer-scoped, api-guidelines §5.5). {@link #findOpenByCustomerId} backs
 * {@code StartCheckoutUseCase}'s already-open-session guard (security review gate: reservation-spam
 * DoS mitigation) via {@code idx_checkout_sessions_customer_id}.
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and
 * {@code repository_interfaces_live_in_domain}.
 */
public interface CheckoutSessionRepository {

    /** Inserts a brand-new session. Called once per session, by {@code StartCheckoutUseCase}. */
    CheckoutSession create(CheckoutSession session);

    /** Persists every mutable field of an already-created session (optimistic-locked via {@code version}). */
    CheckoutSession save(CheckoutSession session);

    Optional<CheckoutSession> findById(CheckoutSessionId id);

    /** Ownership-scoped lookup — returns empty for a nonexistent id OR one owned by someone else. */
    Optional<CheckoutSession> findByIdAndCustomerId(CheckoutSessionId id, CustomerId customerId);

    /**
     * @param customerId     the owning customer — idempotency keys are scoped per customer, not
     *                       global (api-guidelines §5.5)
     * @param idempotencyKey the client-generated key; never {@code null} at the call site
     */
    Optional<CheckoutSession> findByCustomerIdAndIdempotencyKey(CustomerId customerId, String idempotencyKey);

    /**
     * Returns this customer's currently-open session ({@code PENDING_CONFIRMATION} or
     * {@code AWAITING_PAYMENT}), if any. Backs {@code StartCheckoutUseCase}'s guard against a
     * customer starting a second checkout session — and therefore a second stock reservation —
     * while one is already in flight (security review gate, HIGH: reservation-spam / stock-lock
     * DoS). A customer whose only open session has genuinely expired is naturally excluded once
     * {@code ExpirePaymentWindowUseCase}'s sweep has transitioned it to {@code EXPIRED} — see that
     * use case's javadoc and {@code StartCheckoutUseCase}'s for the accepted, bounded window
     * between expiry and the next sweep tick.
     */
    Optional<CheckoutSession> findOpenByCustomerId(CustomerId customerId);

    /** Serves the payment-window expiry sweep ({@code ExpirePaymentWindowUseCase}). */
    List<CheckoutSession> findAwaitingPaymentExpiredAsOf(Instant now);
}

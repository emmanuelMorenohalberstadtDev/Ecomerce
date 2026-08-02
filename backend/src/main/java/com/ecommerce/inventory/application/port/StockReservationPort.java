package com.ecommerce.inventory.application.port;

import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Outbound-facing public façade inventory exposes to other bounded contexts that need to reserve
 * or release stock without depending on inventory's internal domain model or its concrete use
 * cases ({@code ReserveStockUseCase}, {@code ReleaseReservationUseCase} — forbidden targets per
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}, ADR-0003).
 *
 * <p><strong>Why this exists:</strong> checkout needs to reserve stock for a confirmed total and
 * release it on payment-window expiry (domain-model.md §1 checkout, rules 5/6/7). Mirrors
 * {@code catalog.application.port.ProductLookupPort} exactly.
 *
 * <p>Implemented by {@code inventory.infrastructure.adapter.StockReservationAdapter}, delegating
 * to {@code ReserveStockUseCase}/{@code ReleaseReservationUseCase} — each call executes in that
 * use case's own {@code @Transactional} boundary (backend-architecture §3: "each port call
 * executes in the target's own transaction").
 */
public interface StockReservationPort {

    /**
     * @throws InsufficientStockException if any line's stock cannot be fully reserved
     *         (all-or-nothing per reservation) — translated from inventory's own
     *         {@code InsufficientStockException}
     */
    ReservationOutcome reserve(ReservationRequest request);

    /**
     * @throws InvalidReservationStateException if the reservation is not currently HELD —
     *         translated from inventory's own {@code InvalidReservationStateException}
     */
    void release(ReservationId reservationId);

    /** A batch reservation request for one checkout session. */
    record ReservationRequest(CheckoutSessionId checkoutSessionId, List<ReservationLine> lines, Instant expiresAt) {

        public ReservationRequest {
            Objects.requireNonNull(checkoutSessionId, "ReservationRequest checkoutSessionId must not be null");
            Objects.requireNonNull(lines, "ReservationRequest lines must not be null");
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("ReservationRequest lines must not be empty");
            }
            lines = List.copyOf(lines);
        }
    }

    /** One requested (product, quantity) pair. */
    record ReservationLine(ProductId productId, Quantity quantity) {

        public ReservationLine {
            Objects.requireNonNull(productId, "ReservationLine productId must not be null");
            Objects.requireNonNull(quantity, "ReservationLine quantity must not be null");
        }
    }

    /** The reservation created, echoing back the lines actually held. */
    record ReservationOutcome(ReservationId reservationId, List<ReservationLine> lines) {

        public ReservationOutcome {
            Objects.requireNonNull(reservationId, "ReservationOutcome reservationId must not be null");
            Objects.requireNonNull(lines, "ReservationOutcome lines must not be null");
            lines = List.copyOf(lines);
        }
    }

    /**
     * Unchecked, nested exception owned by this façade — never
     * {@code inventory.domain.exception.InsufficientStockException}, which a cross-context caller
     * must not import (ADR-0003 §Decision item 2).
     */
    class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    /**
     * Unchecked, nested exception owned by this façade — never
     * {@code inventory.domain.exception.InvalidReservationStateException}, which a cross-context
     * caller must not import (ADR-0003 §Decision item 2).
     */
    class InvalidReservationStateException extends RuntimeException {
        public InvalidReservationStateException(String message) {
            super(message);
        }
    }
}

package com.ecommerce.checkout.domain.event;

import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Published after a {@code CheckoutSession} reaches {@code AWAITING_PAYMENT} with a confirmed
 * stock reservation (ADR-0003 §Decision item 4) — the order hand-off seam. {@code order}
 * subscribes to this from its own {@code order.application.listener} package (ADR-0003
 * Consequences; ADR-0004), following the {@code CartMergeEventListener} precedent.
 *
 * <p><strong>{@code lines} (ADR-0004 §Decision item 1):</strong> added so {@code order} can build
 * its immutable {@code LineSnapshot}s (rule 3: never re-priced, never re-reads catalog) from the
 * exact {@code RecalculatedTotal} checkout already confirmed and reserved against — zero
 * re-derivation window. Product **name** is deliberately NOT carried here (not price-sensitive;
 * would force checkout to take on a catalog dependency it doesn't otherwise need) — {@code order}
 * resolves the name itself, per line, at listener time via its own
 * {@code order.domain.port.out.ProductCatalogPort}.
 *
 * <p><strong>{@code discountTotal}/{@code shippingTotal} — implementation completion beyond
 * ADR-0004's literal text, flagged for architect sign-off:</strong> ADR-0004 §Decision item 1 only
 * names {@code lines} explicitly, but {@code order.domain.model.OrderTotals} needs the full
 * items/discount/shipping breakdown (domain-model.md §2 {@code OrderTotals (items, discount,
 * shipping, grand — Money)}), and only {@code grandTotal} pre-existed on this event —
 * {@code itemsTotal} is safely derivable in-listener as {@code sum(lines[].lineTotal())} (pure
 * arithmetic on already-trusted data, no re-derivation), but {@code discountTotal}/
 * {@code shippingTotal} are not decomposable from {@code grandTotal} alone. These two fields are
 * added here from the exact same in-memory {@code RecalculatedTotal} ADR-0004 already named as the
 * source for {@code lines} ({@code session.getRecalculatedTotal().discountTotal()}/
 * {@code .shippingFee()}) — the same zero-consumer, safe-breaking-change reasoning applies
 * verbatim. No new business rule invented; a data-completeness gap the ADR's line-focused review
 * did not anticipate.
 *
 * <p>Payload carries ids and minimal facts, never the full aggregate (domain-model.md §4).
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record CheckoutAwaitingPaymentEvent(CheckoutSessionId checkoutSessionId, CustomerId customerId,
                                           CartId cartId, ReservationId reservationId, List<Line> lines,
                                           Money discountTotal, Money shippingTotal, Money grandTotal,
                                           Instant paymentDeadline, Instant occurredAt) {

    public CheckoutAwaitingPaymentEvent {
        if (checkoutSessionId == null) throw new IllegalArgumentException("checkoutSessionId must not be null");
        if (customerId == null) throw new IllegalArgumentException("customerId must not be null");
        if (cartId == null) throw new IllegalArgumentException("cartId must not be null");
        if (reservationId == null) throw new IllegalArgumentException("reservationId must not be null");
        if (lines == null) throw new IllegalArgumentException("lines must not be null");
        if (lines.isEmpty()) throw new IllegalArgumentException("lines must not be empty");
        if (discountTotal == null) throw new IllegalArgumentException("discountTotal must not be null");
        if (shippingTotal == null) throw new IllegalArgumentException("shippingTotal must not be null");
        if (grandTotal == null) throw new IllegalArgumentException("grandTotal must not be null");
        if (paymentDeadline == null) throw new IllegalArgumentException("paymentDeadline must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
        lines = List.copyOf(lines);
    }

    /**
     * One priced line at the moment checkout confirmed and reserved it — the source data for
     * {@code order}'s {@code LineSnapshot} (ADR-0004 §Decision item 1). Mirrors checkout's own
     * {@code RecalculatedLine} field-for-field; declared independently here (event payloads are
     * never a shared type across contexts).
     */
    public record Line(ProductId productId, Quantity quantity, Money unitPrice, Money lineTotal) {

        public Line {
            Objects.requireNonNull(productId, "Line productId must not be null");
            Objects.requireNonNull(quantity, "Line quantity must not be null");
            Objects.requireNonNull(unitPrice, "Line unitPrice must not be null");
            Objects.requireNonNull(lineTotal, "Line lineTotal must not be null");
        }
    }
}

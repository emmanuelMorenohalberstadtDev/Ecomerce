package com.ecommerce.order.domain.model;

import com.ecommerce.shared.money.Money;

import java.util.Objects;

/**
 * Snapshot totals frozen at order-placement time (domain-model.md §2 order {@code OrderTotals
 * (items, discount, shipping, grand — Money)}; rule 3). Maps 1:1 to {@code orders.items_total} /
 * {@code discount_total} / {@code shipping_total} / {@code grand_total}
 * ({@code V006__create_order_schema.sql}) — never re-derived via a join to pricing/cart/catalog
 * after placement.
 *
 * <p>All four amounts share one currency (one order, one currency,
 * {@code V006}'s {@code orders.currency} column) — enforced here by requiring every
 * {@link Money#currency()} to match.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record OrderTotals(Money itemsTotal, Money discountTotal, Money shippingTotal, Money grandTotal) {

    public OrderTotals {
        Objects.requireNonNull(itemsTotal, "OrderTotals itemsTotal must not be null");
        Objects.requireNonNull(discountTotal, "OrderTotals discountTotal must not be null");
        Objects.requireNonNull(shippingTotal, "OrderTotals shippingTotal must not be null");
        Objects.requireNonNull(grandTotal, "OrderTotals grandTotal must not be null");
        String currency = itemsTotal.currency();
        if (!discountTotal.currency().equals(currency) || !shippingTotal.currency().equals(currency)
                || !grandTotal.currency().equals(currency)) {
            throw new IllegalArgumentException(
                    "OrderTotals amounts must all share one currency, got: " + itemsTotal.currency()
                            + ", " + discountTotal.currency() + ", " + shippingTotal.currency()
                            + ", " + grandTotal.currency());
        }
    }

    public String currency() {
        return grandTotal.currency();
    }
}

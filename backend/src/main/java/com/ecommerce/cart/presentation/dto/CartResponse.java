package com.ecommerce.cart.presentation.dto;

import java.util.List;

/**
 * Wire shape for a cart, returned by every cart endpoint (api-guidelines.md §2: "return what the
 * client needs next").
 *
 * <p>{@code subtotal} is a display convenience only — the sum of {@code lineTotal}s, same
 * currency, {@code null} when the cart is empty. It is NOT authoritative: checkout re-prices from
 * live catalog/pricing before payment (domain-model.md §1 pricing: "the server-side price
 * authority"); this cart-side sum exists purely so the frontend has something to render without
 * duplicating arithmetic client-side (api-guidelines.md §8.5 spirit, applied to display rather
 * than checkout).
 */
public record CartResponse(
        String id,
        String status,
        List<CartLineResponse> items,
        MoneyDto subtotal
) {}

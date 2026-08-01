package com.ecommerce.cart.presentation.mapper;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.CartLine;
import com.ecommerce.cart.presentation.dto.CartLineResponse;
import com.ecommerce.cart.presentation.dto.CartResponse;
import com.ecommerce.cart.presentation.dto.MoneyDto;
import com.ecommerce.shared.money.Money;

/**
 * Pure domain-to-DTO conversion for {@link Cart}. No behavior beyond shape translation and the
 * display-only subtotal sum — no framework imports. Mirrors {@code catalog.presentation.mapper.ProductMapper}.
 */
public final class CartMapper {

    private CartMapper() {}

    public static CartResponse toResponse(Cart cart) {
        var items = cart.getLines().stream().map(CartMapper::toLineResponse).toList();
        return new CartResponse(
                cart.getId().toString(),
                cart.getStatus().name(),
                items,
                subtotal(cart)
        );
    }

    private static CartLineResponse toLineResponse(CartLine line) {
        Money unitPrice = line.snapshot().unitPrice();
        Money lineTotal = unitPrice.multiply(line.quantity().value());
        return new CartLineResponse(
                line.productId().toString(),
                line.snapshot().name(),
                toMoneyDto(unitPrice),
                line.quantity().value(),
                line.unavailable(),
                toMoneyDto(lineTotal)
        );
    }

    private static MoneyDto subtotal(Cart cart) {
        var lines = cart.getLines();
        if (lines.isEmpty()) {
            return null;
        }
        Money running = Money.zero(lines.get(0).snapshot().unitPrice().currency());
        for (CartLine line : lines) {
            running = running.add(line.snapshot().unitPrice().multiply(line.quantity().value()));
        }
        return toMoneyDto(running);
    }

    private static MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency());
    }
}

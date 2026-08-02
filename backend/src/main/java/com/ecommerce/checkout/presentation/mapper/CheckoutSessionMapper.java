package com.ecommerce.checkout.presentation.mapper;

import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.presentation.dto.CheckoutSessionResponse;
import com.ecommerce.checkout.presentation.dto.MoneyDto;
import com.ecommerce.shared.money.Money;

/**
 * Pure domain-to-DTO conversion for {@link CheckoutSession}. No behavior beyond shape translation
 * — no framework imports. Mirrors {@code cart.presentation.mapper.CartMapper}.
 */
public final class CheckoutSessionMapper {

    private CheckoutSessionMapper() {}

    public static CheckoutSessionResponse toResponse(CheckoutSession session) {
        RecalculatedTotal total = session.getRecalculatedTotal();
        return new CheckoutSessionResponse(
                session.getId().toString(),
                session.getStatus().name(),
                session.getCartId().toString(),
                session.getReservationId() == null ? null : session.getReservationId().toString(),
                toMoneyDto(total.subtotal()),
                toMoneyDto(total.discountTotal()),
                toMoneyDto(total.shippingFee()),
                toMoneyDto(total.grandTotal()),
                toMoneyDto(session.getExpectedTotal()),
                session.getPaymentDeadline() == null ? null : session.getPaymentDeadline().toString(),
                session.getCreatedAt().toString(),
                session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString());
    }

    private static MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency());
    }
}

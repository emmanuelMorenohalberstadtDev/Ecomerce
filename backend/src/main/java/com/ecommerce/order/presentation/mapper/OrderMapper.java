package com.ecommerce.order.presentation.mapper;

import com.ecommerce.order.domain.OrderRepository.OrderSummary;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatusTransition;
import com.ecommerce.order.presentation.dto.MoneyDto;
import com.ecommerce.order.presentation.dto.OrderLineResponse;
import com.ecommerce.order.presentation.dto.OrderResponse;
import com.ecommerce.order.presentation.dto.OrderStatusTransitionResponse;
import com.ecommerce.order.presentation.dto.OrderSummaryResponse;
import com.ecommerce.shared.money.Money;

/**
 * Pure domain-to-DTO conversion for {@link Order}. No behavior beyond shape translation — no
 * framework imports. Mirrors {@code checkout.presentation.mapper.CheckoutSessionMapper}.
 */
public final class OrderMapper {

    private OrderMapper() {}

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId().toString(),
                order.getStatus().name(),
                order.getLines().stream().map(OrderMapper::toLineResponse).toList(),
                toMoneyDto(order.getTotals().itemsTotal()),
                toMoneyDto(order.getTotals().discountTotal()),
                toMoneyDto(order.getTotals().shippingTotal()),
                toMoneyDto(order.getTotals().grandTotal()),
                order.getCouponCode(),
                order.getStatusHistory().stream().map(OrderMapper::toTransitionResponse).toList(),
                order.getPlacedAt().toString(),
                order.getUpdatedAt() == null ? null : order.getUpdatedAt().toString());
    }

    public static OrderSummaryResponse toSummaryResponse(OrderSummary summary) {
        return new OrderSummaryResponse(
                summary.id().toString(),
                summary.status(),
                toMoneyDto(summary.grandTotal()),
                summary.placedAt().toString());
    }

    private static OrderLineResponse toLineResponse(LineSnapshot line) {
        return new OrderLineResponse(line.productId().toString(), line.productName(),
                toMoneyDto(line.unitPrice()), line.quantity().value());
    }

    private static OrderStatusTransitionResponse toTransitionResponse(OrderStatusTransition transition) {
        return new OrderStatusTransitionResponse(
                transition.from() == null ? null : transition.from().name(),
                transition.to().name(),
                transition.actor().type().name(),
                transition.occurredAt().toString());
    }

    private static MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency());
    }
}

package com.ecommerce.payment.presentation.mapper;

import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase.SubmitPaymentResult;
import com.ecommerce.payment.presentation.dto.MoneyDto;
import com.ecommerce.payment.presentation.dto.PaymentResponse;
import com.ecommerce.shared.money.Money;

/**
 * Pure domain/result-to-DTO conversion for payment. No behavior beyond shape translation — no
 * framework imports. Mirrors {@code order.presentation.mapper.OrderMapper}.
 */
public final class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentResponse toResponse(SubmitPaymentResult result) {
        return new PaymentResponse(
                result.paymentId().toString(),
                result.status().name(),
                result.outcome().name(),
                result.declineReason() == null ? null : result.declineReason().name(),
                result.gatewayReference(),
                toMoneyDto(result.amount()),
                result.attemptedAt().toString());
    }

    private static MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency());
    }
}

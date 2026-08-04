package com.ecommerce.payment.presentation.dto;

/**
 * api-guidelines §1.7: amount is a decimal string, never a bare number. Deliberate duplicate of
 * {@code order.presentation.dto.MoneyDto}/{@code checkout.presentation.dto.MoneyDto} — each
 * bounded context owns its own presentation DTOs (no shared presentation module in this codebase).
 */
public record MoneyDto(String amount, String currency) {
}

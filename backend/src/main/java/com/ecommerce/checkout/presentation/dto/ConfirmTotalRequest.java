package com.ecommerce.checkout.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/checkout-sessions/{id}/confirmation}.
 *
 * <p>{@code confirmedTotal} is the total the customer was just shown (the session's current
 * recalculated total) and is now confirming — carried only for comparison against a freshly
 * re-verified server-side total, never as arithmetic input (api-guidelines §8.5).
 */
public record ConfirmTotalRequest(

        @NotNull(message = "confirmedTotal must be provided")
        @Valid
        MoneyDto confirmedTotal

) {}

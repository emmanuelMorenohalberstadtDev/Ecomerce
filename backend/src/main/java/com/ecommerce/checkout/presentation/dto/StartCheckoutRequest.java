package com.ecommerce.checkout.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/checkout-sessions}.
 *
 * <p>{@code expectedTotal} is the total the client/cart believed BEFORE server-side
 * recalculation — carried only for re-confirmation comparison, never as arithmetic input
 * (api-guidelines §8.5, security-architecture §1.4 threat 2: "any client-sent amount is ignored
 * by design"). No {@code customerId}/{@code cartId} field here — the caller's identity and their
 * active cart are both resolved server-side (api-guidelines §7.4: "no caller identity in request
 * bodies").
 */
public record StartCheckoutRequest(

        @NotNull(message = "expectedTotal must be provided")
        @Valid
        MoneyDto expectedTotal

) {}

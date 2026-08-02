package com.ecommerce.checkout.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Wire shape for money (api-guidelines.md §1.7): always the object
 * {@code {"amount": "19.99", "currency": "USD"}} — {@code amount} is a string so no client ever
 * passes a price through a float. Checkout's own copy (not cart's or catalog's) — presentation
 * DTOs are not shared across context boundaries (would violate
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}).
 */
public record MoneyDto(

        @NotBlank(message = "amount must not be blank")
        @Pattern(regexp = "^\\d{1,15}(\\.\\d{1,4})?$",
                message = "amount must be a non-negative decimal string, e.g. \"19.99\"")
        String amount,

        @NotBlank(message = "currency must not be blank")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO-4217 code")
        String currency

) {}

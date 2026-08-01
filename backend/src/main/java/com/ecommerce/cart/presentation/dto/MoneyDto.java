package com.ecommerce.cart.presentation.dto;

/**
 * Wire shape for money (api-guidelines.md §1.7): always the object
 * {@code {"amount": "19.99", "currency": "USD"}} — {@code amount} is a string so no client ever
 * passes a price through a float. Cart's own copy (not catalog's {@code MoneyDto}) — presentation
 * DTOs are not shared across context boundaries (would violate
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}).
 */
public record MoneyDto(String amount, String currency) {}

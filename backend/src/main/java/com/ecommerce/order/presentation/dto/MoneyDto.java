package com.ecommerce.order.presentation.dto;

/**
 * Wire shape for money (api-guidelines.md §1.7): always the object
 * {@code {"amount": "19.99", "currency": "USD"}} — {@code amount} is a string so no client ever
 * passes a price through a float. Order's own copy (not checkout's/cart's/catalog's) —
 * presentation DTOs are not shared across context boundaries (would violate
 * {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}). Response-only in
 * this context (order never accepts a client-supplied money value — every order value is server
 * computed), so no Bean Validation annotations are needed here, unlike checkout's request-bound copy.
 */
public record MoneyDto(String amount, String currency) {}

package com.ecommerce.order.presentation.dto;

/**
 * Wire shape for one status-history entry (rule 12). {@code from} is {@code null} for the initial
 * PLACED row. {@code occurredAt} is ISO-8601 UTC (api-guidelines §1.6).
 */
public record OrderStatusTransitionResponse(String from, String to, String actorType, String occurredAt) {}

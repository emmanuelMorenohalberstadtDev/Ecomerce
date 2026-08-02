package com.ecommerce.checkout.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when {@code checkout.domain.port.out.PriceQuotePort} cannot price a checkout session's
 * lines because one of the customer's cart lines names a product catalog reports as nonexistent
 * or not ACTIVE (retired) — translated by {@code checkout.infrastructure.adapter.PriceQuoteAdapter}
 * from pricing's façade-owned {@code PriceCalculationPort.ProductUnavailableException}, which
 * checkout must not import directly (ADR-0003 §Decision item 2). Mirrors
 * {@code pricing.domain.exception.ProductNotAvailableException} /
 * {@code cart.domain.exception.ProductNotAvailableException} exactly.
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}'s generic {@code BusinessRuleViolationException}
 * handler.
 */
public final class ProductUnavailableForCheckoutException extends BusinessRuleViolationException {

    public ProductUnavailableForCheckoutException(String message) {
        super(message);
    }
}

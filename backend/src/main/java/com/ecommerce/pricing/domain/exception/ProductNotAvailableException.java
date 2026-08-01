package com.ecommerce.pricing.domain.exception;

import com.ecommerce.common.web.exception.BusinessRuleViolationException;

/**
 * Thrown when price calculation targets a product that catalog reports as nonexistent or not
 * ACTIVE (retired).
 *
 * <p>Maps to HTTP 422 via {@code ApiExceptionHandler}, not 404: mirrors
 * {@code cart.domain.exception.ProductNotAvailableException} exactly, same semantics and same
 * reasoning — a well-formed price request naming a product id that turns out unusable is
 * "well-formed but semantically rejected by a business rule" (api-guidelines.md §2), not "the
 * addressed resource does not exist". {@link com.ecommerce.pricing.domain.port.out.ProductCatalogPort#findActiveById}
 * treats RETIRED as indistinguishable from nonexistent, mirroring catalog's own
 * {@code ProductLookupPort.findActiveById} convention, so this single exception naturally covers
 * both cases without pricing needing to know which one occurred.
 */
public final class ProductNotAvailableException extends BusinessRuleViolationException {

    public ProductNotAvailableException(String message) {
        super(message);
    }
}

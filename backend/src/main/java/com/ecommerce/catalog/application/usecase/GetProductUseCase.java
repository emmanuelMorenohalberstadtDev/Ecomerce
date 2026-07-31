package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.id.ProductId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Fetches one product by id for the public catalog surface.
 *
 * <p>Auth decision table row: GUEST — 200 (ACTIVE only); CUSTOMER — 200 (ACTIVE only);
 * ADMIN — 200 (ACTIVE only) — security-architecture §3.4 seed row: "Public catalog reads ...
 * ACTIVE products only for non-admin". This use case backs only the public
 * {@code GET /api/v1/products/{id}} endpoint, so it never distinguishes caller role — a
 * {@code RETIRED} product is 404 to every caller here, admins included. There is no separate
 * "admin can see retired products by id" endpoint in v1 (flagged as an open follow-up in the
 * catalog handoff — admin mutation use cases load products directly via
 * {@link ProductRepository#findById}, which is status-agnostic, but no admin *read* endpoint
 * exposes that today).
 *
 * <p>Read-only transaction boundary.
 */
@Service
@Transactional(readOnly = true)
public class GetProductUseCase {

    private final ProductRepository productRepository;

    public GetProductUseCase(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository);
    }

    public Product execute(String productIdRaw) {
        ProductId productId = ProductId.of(productIdRaw);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (!product.isActive()) {
            // RETIRED is indistinguishable from nonexistent on the public surface.
            throw new ProductNotFoundException("Product not found: " + productId);
        }
        return product;
    }
}

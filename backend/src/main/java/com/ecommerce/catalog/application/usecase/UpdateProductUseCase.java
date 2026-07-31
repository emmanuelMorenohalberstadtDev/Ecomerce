package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.ProductImage;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.id.ProductId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Updates a product's mutable catalog details: owning category, name, description, and images.
 * Admin-only mutation. SKU, price, and status have their own dedicated use cases
 * ({@link ChangeProductPriceUseCase}, {@link RetireProductUseCase}) — never touched here.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx (route layer +
 * {@link PreAuthorize} defense-in-depth, security-architecture §3.2).
 *
 * <p>Permitted regardless of the product's current status (ACTIVE or RETIRED) — retiring is a
 * one-way sales-availability transition (rule 10), not a freeze on correcting catalog data such
 * as a typo in the name/description of an already-retired product. If product-owner wants
 * RETIRED products to become fully immutable, that is a business-rule change to route back
 * through product-owner, not something invented here.
 *
 * <p>Transaction boundary is this use case class; the audit row is written in the same
 * transaction as the update (security §6c requirement 3).
 */
@Service
@Transactional
public class UpdateProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogPort auditLogPort;

    public UpdateProductUseCase(ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                AuditLogPort auditLogPort) {
        this.productRepository = Objects.requireNonNull(productRepository);
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Product execute(UpdateProductCommand command) {
        ProductId productId = ProductId.of(command.productId());
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        CategoryId categoryId = CategoryId.of(command.categoryId());
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        List<ProductImage> images = CreateProductUseCase.toDomainImages(command.images());
        product.updateDetails(category.getId(), command.name(), command.description(), images);

        Product saved = productRepository.save(product);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", saved.getName());
        details.put("categoryId", saved.getCategoryId().toString());
        auditLogPort.record(CatalogAuditAction.PRODUCT_UPDATED, "Product", saved.getId().toString(), details);

        return saved;
    }

    public record UpdateProductCommand(String productId, String categoryId, String name,
                                       String description, List<ProductImageInput> images) {}
}

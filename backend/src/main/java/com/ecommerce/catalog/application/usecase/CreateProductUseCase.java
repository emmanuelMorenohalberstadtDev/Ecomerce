package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.exception.DuplicateSkuException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.ProductImage;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.money.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates a new product in the catalog. Admin-only mutation.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx. Enforced at the
 * route layer by {@code AuthSecurityConfiguration} ({@code /api/v1/admin/**} requires
 * {@code ADMIN}) and again here via {@link PreAuthorize} as the fine-grained second layer
 * (security-architecture §3.2).
 *
 * <p>Business rules: the target category must already exist ({@link CategoryNotFoundException},
 * 404); the SKU must not already identify another product ({@link DuplicateSkuException}, 409).
 * Writes one {@code PRODUCT_CREATED} row to the admin audit log in the same transaction as the
 * insert (security §6c requirement 3) — if the audit write fails, the product creation rolls
 * back with it.
 *
 * <p>Transaction boundary is this use case class (backend-architecture §4.1).
 */
@Service
@Transactional
public class CreateProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogPort auditLogPort;

    public CreateProductUseCase(ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                AuditLogPort auditLogPort) {
        this.productRepository = Objects.requireNonNull(productRepository);
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Product execute(CreateProductCommand command) {
        CategoryId categoryId = CategoryId.of(command.categoryId());
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found: " + categoryId));

        Sku sku = new Sku(command.sku());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("SKU already in use: " + sku);
        }

        Money basePrice = new Money(command.basePriceAmount(), command.currency());
        List<ProductImage> images = toDomainImages(command.images());

        Product product = Product.create(category.getId(), sku, basePrice,
                command.name(), command.description(), images);
        Product saved = productRepository.save(product);

        auditLogPort.record(CatalogAuditAction.PRODUCT_CREATED, "Product", saved.getId().toString(),
                auditDetails(saved));

        return saved;
    }

    static List<ProductImage> toDomainImages(List<ProductImageInput> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .map(i -> new ProductImage(i.imageUrl(), i.displayOrder()))
                .toList();
    }

    private static Map<String, Object> auditDetails(Product product) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sku", product.getSku().value());
        details.put("name", product.getName());
        details.put("categoryId", product.getCategoryId().toString());
        details.put("basePrice", product.getBasePrice().toString());
        return details;
    }

    /**
     * @param categoryId       existing category's id
     * @param sku              stock-keeping unit, normalized by {@link Sku}
     * @param basePriceAmount  list price amount
     * @param currency         ISO-4217 currency code
     * @param name             product name
     * @param description      nullable long-form description
     * @param images           nullable/empty ordered image list
     */
    public record CreateProductCommand(String categoryId, String sku, BigDecimal basePriceAmount,
                                       String currency, String name, String description,
                                       List<ProductImageInput> images) {}
}

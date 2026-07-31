package com.ecommerce.catalog.domain.model;

import com.ecommerce.catalog.domain.exception.ProductAlreadyRetiredException;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root for the catalog bounded context.
 *
 * <p>Owns product identity, current base price, ACTIVE/RETIRED availability, and an ordered
 * list of image URLs. References its owning category by {@link CategoryId} only — never holds
 * an object reference to the {@code Category} aggregate (domain-model.md §2 ownership rules).
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>Retiring never deletes — it is a one-way status transition
 *       {@code ACTIVE -> RETIRED} (domain-model.md §1 catalog, rule 10).</li>
 *   <li>Retiring an already-retired product is rejected, not silently accepted — see
 *       {@link #retire()} javadoc for the rationale.</li>
 *   <li>Changing the price never happens silently: the caller (the use case) is expected to
 *       read {@link #getBasePrice()} before and after calling {@link #changePrice(Money)} to
 *       raise {@code ProductPriceChangedEvent} with old/new values, mirroring how
 *       {@code RegisterUserUseCase} builds {@code UserRegisteredEvent} from state it read off
 *       the aggregate after the domain method returned — domain objects here do not carry an
 *       internal pending-events list.</li>
 *   <li>Image display order is unique per product (mirrors the DB's
 *       {@code uq_product_images_product_display} constraint) — enforced defensively at
 *       construction/update time so a bug never reaches the database as a constraint violation.</li>
 * </ul>
 *
 * <p>Created via {@link #create} factory; reconstituted from persistence via
 * {@link #reconstitute}. No public setters — state transitions happen through named domain
 * methods that express intent.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class Product {

    private final ProductId id;
    private CategoryId categoryId;
    private final Sku sku;
    private ProductStatus status;
    private Money basePrice;
    private String name;
    private String description;
    private final List<ProductImage> images;

    private Product(ProductId id, CategoryId categoryId, Sku sku, ProductStatus status,
                    Money basePrice, String name, String description, List<ProductImage> images) {
        if (id == null) throw new IllegalArgumentException("ProductId must not be null");
        if (categoryId == null) throw new IllegalArgumentException("CategoryId must not be null");
        if (sku == null) throw new IllegalArgumentException("Sku must not be null");
        if (status == null) throw new IllegalArgumentException("ProductStatus must not be null");
        if (basePrice == null) throw new IllegalArgumentException("basePrice must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be null or blank");
        }

        this.id = id;
        this.categoryId = categoryId;
        this.sku = sku;
        this.status = status;
        this.basePrice = basePrice;
        this.name = name;
        this.description = description;
        this.images = new ArrayList<>(validateImageOrder(images));
    }

    /**
     * Factory for creating a brand-new product. Assigns a fresh {@link ProductId} and starts
     * {@code ACTIVE}.
     */
    public static Product create(CategoryId categoryId, Sku sku, Money basePrice, String name,
                                 String description, List<ProductImage> images) {
        return new Product(ProductId.generate(), categoryId, sku, ProductStatus.ACTIVE,
                basePrice, name, description, images);
    }

    /**
     * Factory for reconstitution from persistence. Restores exact state without re-running
     * creation business rules.
     */
    public static Product reconstitute(ProductId id, CategoryId categoryId, Sku sku,
                                       ProductStatus status, Money basePrice, String name,
                                       String description, List<ProductImage> images) {
        return new Product(id, categoryId, sku, status, basePrice, name, description, images);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Updates mutable catalog details: owning category, name, description, and images.
     * SKU, price, and status are never touched here — they have their own dedicated
     * behaviour ({@link #changePrice}, {@link #retire}) because they carry distinct
     * business rules and audit semantics.
     */
    public void updateDetails(CategoryId categoryId, String name, String description,
                              List<ProductImage> images) {
        if (categoryId == null) throw new IllegalArgumentException("CategoryId must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be null or blank");
        }
        List<ProductImage> validatedImages = validateImageOrder(images);

        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.images.clear();
        this.images.addAll(validatedImages);
    }

    /**
     * Changes the current base (list) price.
     *
     * <p>Does not raise a domain event itself — the caller reads {@link #getBasePrice()} before
     * invoking this method and again after to construct {@code ProductPriceChangedEvent} with
     * both values, then publishes it after commit (mirrors the auth use-case event pattern).
     */
    public void changePrice(Money newPrice) {
        if (newPrice == null) throw new IllegalArgumentException("newPrice must not be null");
        this.basePrice = newPrice;
    }

    /**
     * Retires the product: {@code ACTIVE -> RETIRED}. Never deletes the row (rule 10).
     *
     * <p><strong>Design choice — rejects re-retirement:</strong> calling this on an
     * already-{@code RETIRED} product throws {@link ProductAlreadyRetiredException} rather than
     * silently no-op-ing. Rationale: retirement is an audited admin action (security §6c) with
     * its own event; silently swallowing a second retirement would produce a misleading audit
     * trail ("retired" logged twice for one real transition) and hides a client bug (e.g. a
     * double-submitted admin form) that is cheap to surface as a 409 rather than mask.
     */
    public void retire() {
        if (this.status == ProductStatus.RETIRED) {
            throw new ProductAlreadyRetiredException(
                    "Product " + id + " is already retired");
        }
        this.status = ProductStatus.RETIRED;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the product is sellable/visible in public catalog browsing. */
    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public ProductId getId() { return id; }

    public CategoryId getCategoryId() { return categoryId; }

    public Sku getSku() { return sku; }

    public ProductStatus getStatus() { return status; }

    public Money getBasePrice() { return basePrice; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    /** Unmodifiable, defensively-copied view of the product's images, in display order. */
    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(new ArrayList<>(images));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static List<ProductImage> validateImageOrder(List<ProductImage> images) {
        List<ProductImage> safe = images == null ? List.of() : images;
        Set<Integer> seenPositions = new HashSet<>();
        for (ProductImage image : safe) {
            if (!seenPositions.add(image.displayOrder())) {
                throw new IllegalArgumentException(
                        "Duplicate image displayOrder within one product: " + image.displayOrder());
            }
        }
        return safe;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product product)) return false;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

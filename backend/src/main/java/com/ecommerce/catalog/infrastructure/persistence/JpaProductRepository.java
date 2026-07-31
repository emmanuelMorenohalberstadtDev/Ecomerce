package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.catalog.domain.exception.DuplicateSkuException;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.ProductImage;
import com.ecommerce.catalog.domain.model.ProductStatus;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.catalog.domain.port.out.ProductSort;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link ProductRepository} port.
 *
 * <p>Maps between {@link Product} (domain, including its {@link ProductImage} children) and
 * {@link ProductJpaEntity} (JPA, including {@link ProductImageJpaEntity} children). The domain
 * object never touches JPA internals; the JPA entity never escapes this adapter.
 */
public class JpaProductRepository implements ProductRepository {

    private final SpringDataProductDao springDataProductDao;
    private final Clock clock;

    public JpaProductRepository(SpringDataProductDao springDataProductDao, Clock clock) {
        this.springDataProductDao = springDataProductDao;
        this.clock = clock;
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = toEntity(product);
        try {
            ProductJpaEntity saved = springDataProductDao.save(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // Backstop for the race between CreateProductUseCase's existsBySku pre-check and
            // this insert — translates the raw uq_products_sku violation instead of leaking it.
            throw new DuplicateSkuException(
                    "SKU already in use: " + product.getSku(), e);
        }
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return springDataProductDao.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return springDataProductDao.findBySku(sku.value()).map(this::toDomain);
    }

    @Override
    public boolean existsBySku(Sku sku) {
        return springDataProductDao.existsBySku(sku.value());
    }

    @Override
    public boolean existsByCategoryId(CategoryId categoryId) {
        return springDataProductDao.existsByCategoryId(categoryId.value());
    }

    @Override
    public PageResult<Product> searchActive(CategoryId categoryId, String namePrefixLower,
                                            ProductSort sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toSpringSort(sort));
        Page<ProductJpaEntity> result = springDataProductDao.searchActive(
                categoryId == null ? null : categoryId.value(), namePrefixLower, pageable);

        return new PageResult<>(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private static Sort toSpringSort(ProductSort sort) {
        String property = switch (sort.field()) {
            case NAME -> "name";
            case BASE_PRICE -> "basePrice";
        };
        return sort.descending() ? Sort.by(property).descending() : Sort.by(property).ascending();
    }

    private ProductJpaEntity toEntity(Product product) {
        Instant now = clock.instant();
        ProductJpaEntity entity = new ProductJpaEntity(
                product.getId().value(),
                product.getCategoryId().value(),
                product.getSku().value(),
                product.getStatus().name(),
                product.getBasePrice().amount(),
                product.getBasePrice().currency(),
                product.getName(),
                product.getDescription(),
                now // createdAt: ignored on UPDATE because updatable=false
        );
        List<ProductImageJpaEntity> images = product.getImages().stream()
                .map(image -> new ProductImageJpaEntity(entity, image.imageUrl(), image.displayOrder(), now))
                .toList();
        entity.replaceImages(images);
        return entity;
    }

    private Product toDomain(ProductJpaEntity entity) {
        List<ProductImage> images = entity.getImages().stream()
                .map(i -> new ProductImage(i.getImageUrl(), i.getDisplayOrder()))
                .toList();
        return Product.reconstitute(
                ProductId.of(entity.getId()),
                CategoryId.of(entity.getCategoryId()),
                new Sku(entity.getSku()),
                ProductStatus.valueOf(entity.getStatus()),
                new Money(entity.getBasePrice(), entity.getCurrency()),
                entity.getName(),
                entity.getDescription(),
                images
        );
    }
}

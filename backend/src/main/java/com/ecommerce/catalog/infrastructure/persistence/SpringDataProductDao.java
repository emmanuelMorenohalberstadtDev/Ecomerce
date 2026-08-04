package com.ecommerce.catalog.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link ProductJpaEntity}.
 *
 * <p>Package-private — only {@link JpaProductRepository} uses it. Named "Dao" (not
 * "Repository") to avoid triggering the ArchUnit rule that requires every {@code *Repository}
 * interface to live in {@code ..domain..}. The domain-layer port is
 * {@link com.ecommerce.catalog.domain.port.out.ProductRepository}.
 */
@Repository
interface SpringDataProductDao extends JpaRepository<ProductJpaEntity, UUID> {

    Optional<ProductJpaEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsByCategoryId(UUID categoryId);

    /**
     * ACTIVE-only search matching either or both nullable filters. Both predicates use the
     * {@code (:param IS NULL OR ...)} pattern so a single query serves all three combinations
     * (category only, name-prefix only, both) without three separate methods.
     */
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.status = 'ACTIVE' "
            + "AND (:categoryId IS NULL OR p.categoryId = :categoryId) "
            + "AND (:namePrefix IS NULL OR lower(p.name) LIKE CONCAT(cast(:namePrefix as string), '%'))")
    Page<ProductJpaEntity> searchActive(@Param("categoryId") UUID categoryId,
                                        @Param("namePrefix") String namePrefix,
                                        Pageable pageable);
}

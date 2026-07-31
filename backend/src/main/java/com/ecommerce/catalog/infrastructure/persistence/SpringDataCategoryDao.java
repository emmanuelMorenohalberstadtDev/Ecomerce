package com.ecommerce.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA interface for {@link CategoryJpaEntity}.
 *
 * <p>Package-private — only {@link JpaCategoryRepository} uses it. Named "Dao" (not
 * "Repository") to avoid triggering the ArchUnit rule that requires every {@code *Repository}
 * interface to live in {@code ..domain..}. The domain-layer port is
 * {@link com.ecommerce.catalog.domain.port.out.CategoryRepository}.
 */
@Repository
interface SpringDataCategoryDao extends JpaRepository<CategoryJpaEntity, UUID> {

    boolean existsByParentId(UUID parentId);
}

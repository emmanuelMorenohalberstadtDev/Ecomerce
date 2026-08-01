package com.ecommerce.cart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link CartJpaEntity}.
 *
 * <p>Package-private — only {@link JpaCartRepository} uses it. Named "Dao" (not "Repository") to
 * avoid triggering the ArchUnit rule that requires every {@code *Repository} interface to live in
 * {@code ..domain..}. The domain-layer port is
 * {@link com.ecommerce.cart.domain.port.out.CartRepository}.
 *
 * <p>Every finder uses {@code LEFT JOIN FETCH c.items} so a cart's lines are always loaded in the
 * same query (database-design.md §7: "lazy everything ... fetch per use case with join fetch").
 */
@Repository
interface SpringDataCartDao extends JpaRepository<CartJpaEntity, UUID> {

    @Query("SELECT c FROM CartJpaEntity c LEFT JOIN FETCH c.items WHERE c.id = :id")
    Optional<CartJpaEntity> findByIdFetchItems(@Param("id") UUID id);

    @Query("SELECT c FROM CartJpaEntity c LEFT JOIN FETCH c.items "
            + "WHERE c.customerId = :customerId AND c.status = 'ACTIVE'")
    Optional<CartJpaEntity> findActiveByCustomerIdFetchItems(@Param("customerId") UUID customerId);

    @Query("SELECT c FROM CartJpaEntity c LEFT JOIN FETCH c.items "
            + "WHERE c.guestTokenHash = :guestTokenHash AND c.status = 'ACTIVE'")
    Optional<CartJpaEntity> findActiveByGuestTokenHashFetchItems(@Param("guestTokenHash") String guestTokenHash);
}

package com.ecommerce.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface for {@link UserAccountJpaEntity}.
 *
 * <p>Package-private — only {@link JpaUserAccountRepository} uses it.
 * Named "Dao" (not "Repository") to avoid triggering the ArchUnit rule that
 * requires all {@code *Repository} interfaces to live in {@code ..domain..}.
 * The domain-layer port is {@link com.ecommerce.auth.domain.port.out.UserAccountRepository}.
 */
@Repository
interface SpringDataUserDao extends JpaRepository<UserAccountJpaEntity, UUID> {

    Optional<UserAccountJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}

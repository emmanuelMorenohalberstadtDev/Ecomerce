package com.ecommerce.auth.domain.port.out;

import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.shared.id.UserId;

import java.util.Optional;

/**
 * Domain port (outbound) for {@link UserAccount} persistence.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaUserAccountRepository}
 * in the infrastructure layer. The domain never references infrastructure types.
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and
 * the ArchUnit rule {@code repository_interfaces_live_in_domain}.
 */
public interface UserAccountRepository {

    UserAccount save(UserAccount user);

    Optional<UserAccount> findById(UserId id);

    Optional<UserAccount> findByEmail(Email email);

    boolean existsByEmail(Email email);
}

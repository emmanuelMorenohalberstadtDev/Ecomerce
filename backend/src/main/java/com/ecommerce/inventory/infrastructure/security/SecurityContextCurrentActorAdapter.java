package com.ecommerce.inventory.infrastructure.security;

import com.ecommerce.inventory.application.port.CurrentActorPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Adapter implementing {@link CurrentActorPort} against Spring Security's
 * {@code SecurityContextHolder} — the only class in this context allowed to touch it directly
 * (see {@link CurrentActorPort}'s javadoc for why the use case itself does not).
 *
 * <p>{@code JwtAuthenticationFilter} (auth context) populates the security context with the
 * JWT's {@code sub} claim (the user id) as the principal name, exactly like catalog's
 * {@code AdminAuditLogJpaAdapter} already relies on.
 */
public class SecurityContextCurrentActorAdapter implements CurrentActorPort {

    @Override
    public UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "Cannot resolve current actor without an authenticated principal");
        }
        return UUID.fromString(authentication.getName());
    }
}

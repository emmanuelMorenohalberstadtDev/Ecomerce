package com.ecommerce.order.infrastructure.security;

import com.ecommerce.order.application.port.CurrentActorPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Adapter implementing {@link CurrentActorPort} against Spring Security's
 * {@code SecurityContextHolder} — the only class in this context allowed to touch it directly.
 * Mirrors {@code inventory.infrastructure.security.SecurityContextCurrentActorAdapter} exactly.
 *
 * <p>{@code JwtAuthenticationFilter} (auth context) populates the security context with the
 * JWT's {@code sub} claim (the user id) as the principal name.
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

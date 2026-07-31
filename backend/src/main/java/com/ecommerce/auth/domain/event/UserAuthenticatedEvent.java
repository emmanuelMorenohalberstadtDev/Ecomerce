package com.ecommerce.auth.domain.event;

import com.ecommerce.shared.id.UserId;

import java.time.Instant;

/**
 * Published after a successful login.
 *
 * <p>Consumed (after commit) by: guest-cart merge listener (cart context),
 * auth-event audit log.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record UserAuthenticatedEvent(UserId userId, Instant occurredAt) {

    public UserAuthenticatedEvent {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

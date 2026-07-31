package com.ecommerce.auth.domain.event;

import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.shared.id.UserId;

import java.time.Instant;

/**
 * Published after a new {@code UserAccount} is persisted.
 *
 * <p>Consumed (after commit) by: audit logging, welcome-email trigger (future).
 * Payload carries only what consumers need: the user's id and email.
 * No passwords, hashes, or tokens — event payloads must never contain credentials.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record UserRegisteredEvent(UserId userId, Email email, Instant occurredAt) {

    public UserRegisteredEvent {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (email == null) throw new IllegalArgumentException("email must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }
}

package com.ecommerce.inventory.application.port;

import java.util.UUID;

/**
 * Outbound port resolving the currently-authenticated principal's id.
 *
 * <p><strong>Why this exists (and catalog's audit adapter didn't need it):</strong> catalog's
 * {@code AuditLogPort} adapter reads {@code SecurityContextHolder} directly, entirely inside
 * infrastructure, because the acting admin's id is needed in exactly one place — the audit row
 * it writes itself. {@code AdminAdjustStockUseCase} needs the acting admin's id in a
 * <em>second</em> place too: {@code stock_movements.actor_id}, a column the audit adapter has no
 * involvement in. That forces the id to be available inside the use case (application layer)
 * itself, not just inside an infrastructure adapter.
 *
 * <p>Reaching for {@code org.springframework.security.core.context.SecurityContextHolder}
 * directly from a use case would technically satisfy every ArchUnit rule (it is not a
 * {@code ..infrastructure..}/{@code ..presentation..} package), but it fails the "write code the
 * test-engineer can test without reflection or mocking statics" guidance this feature is built
 * against — {@code SecurityContextHolder} is thread-local static state. This port makes the
 * dependency explicit and mockable instead.
 *
 * <p>Implemented by {@code SecurityContextCurrentActorAdapter} in infrastructure, which is the
 * only class in this context allowed to touch {@code SecurityContextHolder}.
 */
public interface CurrentActorPort {

    /**
     * @return the authenticated principal's user id
     * @throws IllegalStateException if there is no authenticated principal — every caller of this
     *         port is an admin use case already gated by {@code @PreAuthorize} and the
     *         {@code /api/v1/admin/**} route rule, so reaching here unauthenticated means one of
     *         those gates was bypassed; failing loudly is preferable to attributing the action to
     *         an invented actor.
     */
    UUID currentActorId();
}

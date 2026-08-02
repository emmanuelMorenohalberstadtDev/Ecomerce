package com.ecommerce.order.application.port;

import java.util.UUID;

/**
 * Outbound port resolving the currently-authenticated principal's id.
 *
 * <p>Needed inside admin order use cases in two places: {@link com.ecommerce.order.domain.model.Actor#admin}
 * (recorded in-memory on the appended {@code OrderStatusTransition}, even though it is not
 * persisted beyond {@code actor_type} — see {@code Actor}'s javadoc) and the audit row written via
 * {@link AuditLogPort}. Mirrors {@code inventory.application.port.CurrentActorPort} exactly — same
 * rationale for not reaching {@code SecurityContextHolder} directly from a use case (testability
 * without mocking statics).
 *
 * <p>Implemented by {@code order.infrastructure.security.SecurityContextCurrentActorAdapter} in
 * infrastructure, the only class in this context allowed to touch
 * {@code SecurityContextHolder}.
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

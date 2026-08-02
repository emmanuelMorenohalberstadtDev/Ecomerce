package com.ecommerce.order.infrastructure.persistence;

import com.ecommerce.order.application.port.AuditLogPort;
import com.ecommerce.order.application.port.OrderAuditAction;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA adapter implementing {@link AuditLogPort} against the shared {@code admin_audit_log} table.
 *
 * <p>Deliberate duplicate of inventory's/catalog's {@code AdminAuditLogJpaAdapter} — same
 * actor-resolution strategy (reads {@code SecurityContextHolder} directly, entirely inside
 * infrastructure), same {@code actor_email} known limitation (populated with the actor id, not a
 * real email — no PII claim available in the JWT per security-architecture §6b), same
 * un-populated {@code ip_address}.
 *
 * <p><strong>Transaction participation:</strong> no {@code @Transactional} here — joins whichever
 * transaction the calling use case already started, so the audit insert and the audited mutation
 * commit or roll back together (security §6c requirement 3).
 */
public class OrderAuditLogJpaAdapter implements AuditLogPort {

    private final SpringDataOrderAuditLogDao springDataOrderAuditLogDao;
    private final Clock clock;

    public OrderAuditLogJpaAdapter(SpringDataOrderAuditLogDao springDataOrderAuditLogDao, Clock clock) {
        this.springDataOrderAuditLogDao = springDataOrderAuditLogDao;
        this.clock = clock;
    }

    @Override
    public void record(OrderAuditAction action, String entityType, String entityId,
                       Map<String, Object> details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // Every caller of this port is an admin use case gated by @PreAuthorize — reaching
            // here without an authenticated principal means that gate was somehow bypassed.
            // Fail loudly rather than record an unattributable admin action.
            throw new IllegalStateException(
                    "Cannot write admin audit log entry without an authenticated principal");
        }

        UUID actorId = UUID.fromString(authentication.getName());
        String actorRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("UNKNOWN");

        Map<String, Object> enrichedDetails = new LinkedHashMap<>();
        enrichedDetails.put("actorRole", actorRole);
        if (details != null) {
            enrichedDetails.putAll(details);
        }

        OrderAuditLogJpaEntity entity = new OrderAuditLogJpaEntity(
                actorId,
                // actor_email is unavailable here — see class javadoc "known limitation".
                actorId.toString(),
                action.name(),
                entityType,
                entityId,
                enrichedDetails,
                clock.instant());

        springDataOrderAuditLogDao.save(entity);
    }
}

package com.ecommerce.inventory.infrastructure.persistence;

import com.ecommerce.inventory.application.port.AuditLogPort;
import com.ecommerce.inventory.application.port.InventoryAuditAction;
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
 * <p>Deliberate duplicate of catalog's {@code AdminAuditLogJpaAdapter} — same actor-resolution
 * strategy (reads {@code SecurityContextHolder} directly, entirely inside infrastructure), same
 * {@code actor_email} known limitation (populated with the actor id, not a real email — no PII
 * claim available in the JWT per security-architecture §6b, and inventory is equally forbidden
 * from depending on auth's code to look one up), same un-populated {@code ip_address}.
 *
 * <p>Note the distinction from {@code CurrentActorPort}: this adapter resolves the actor
 * <em>only</em> for the {@code admin_audit_log} row it itself writes. {@code stock_movements.actor_id}
 * is populated by {@code AdminAdjustStockUseCase} via the separate {@code CurrentActorPort},
 * because that value is needed inside the use case (application layer), not just here.
 *
 * <p><strong>Transaction participation:</strong> no {@code @Transactional} here — joins whichever
 * transaction the calling use case already started, so the audit insert and the audited mutation
 * commit or roll back together (security §6c requirement 3).
 */
public class InventoryAuditLogJpaAdapter implements AuditLogPort {

    private final SpringDataInventoryAuditLogDao springDataInventoryAuditLogDao;
    private final Clock clock;

    public InventoryAuditLogJpaAdapter(SpringDataInventoryAuditLogDao springDataInventoryAuditLogDao, Clock clock) {
        this.springDataInventoryAuditLogDao = springDataInventoryAuditLogDao;
        this.clock = clock;
    }

    @Override
    public void record(InventoryAuditAction action, String entityType, String entityId,
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

        InventoryAuditLogJpaEntity entity = new InventoryAuditLogJpaEntity(
                actorId,
                // actor_email is unavailable here — see class javadoc "known limitation".
                actorId.toString(),
                action.name(),
                entityType,
                entityId,
                enrichedDetails,
                clock.instant());

        springDataInventoryAuditLogDao.save(entity);
    }
}

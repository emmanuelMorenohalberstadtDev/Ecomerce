package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA adapter implementing {@link AuditLogPort} against the shared {@code admin_audit_log}
 * table.
 *
 * <p><strong>Actor resolution:</strong> reads the acting principal directly from
 * {@code SecurityContextHolder} — a framework type, which is why this read happens here in
 * infrastructure rather than in the application-layer use cases (ADR-0001 dependency rule:
 * application must not depend on infrastructure). {@code JwtAuthenticationFilter} (auth context)
 * populates the security context with the JWT's {@code sub} claim (the user id) as the
 * principal name and {@code ROLE_ADMIN}/{@code ROLE_CUSTOMER} as the sole granted authority.
 *
 * <p><strong>Known limitation — {@code actor_email}:</strong> security-architecture §6b fixes
 * the JWT access-token claims to {@code sub}, {@code roles}, {@code iat}, {@code exp}, {@code jti}
 * only ("no PII in claims") — there is no email in the token, and catalog is deliberately
 * forbidden from depending on any of auth's code (not even {@code UserAccountRepository}) to
 * look one up, per this feature's brief. Since {@code admin_audit_log.actor_email} is
 * {@code NOT NULL}, this adapter populates it with the same value as {@code actor_id} (the
 * user's id, not a real email) rather than fabricating a plausible-looking fake address. This is
 * a real gap, surfaced in the catalog handoff report as a question for
 * software-architect/security-engineer: either add a non-PII-safe claim the audit log can use,
 * or introduce a shared, read-only "current actor" lookup mechanism common to every context.
 *
 * <p><strong>IP address:</strong> not populated in v1 (see {@link AdminAuditLogJpaEntity}
 * javadoc) — the column is nullable, so this is valid, not a broken insert.
 *
 * <p><strong>Transaction participation:</strong> no {@code @Transactional} here — this adapter
 * joins whichever transaction the calling use case (e.g. {@code CreateProductUseCase}) already
 * started, so the audit insert and the audited mutation commit or roll back together
 * (security §6c requirement 3).
 */
public class AdminAuditLogJpaAdapter implements AuditLogPort {

    private final SpringDataAdminAuditLogDao springDataAdminAuditLogDao;
    private final Clock clock;

    public AdminAuditLogJpaAdapter(SpringDataAdminAuditLogDao springDataAdminAuditLogDao, Clock clock) {
        this.springDataAdminAuditLogDao = springDataAdminAuditLogDao;
        this.clock = clock;
    }

    @Override
    public void record(CatalogAuditAction action, String entityType, String entityId,
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

        AdminAuditLogJpaEntity entity = new AdminAuditLogJpaEntity(
                actorId,
                // actor_email is unavailable here — see class javadoc "Known limitation".
                actorId.toString(),
                action.name(),
                entityType,
                entityId,
                enrichedDetails,
                clock.instant());

        springDataAdminAuditLogDao.save(entity);
    }
}

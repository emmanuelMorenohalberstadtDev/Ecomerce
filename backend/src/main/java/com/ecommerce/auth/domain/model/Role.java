package com.ecommerce.auth.domain.model;

/**
 * Flat role enumeration for v1 (security-architecture §3.1).
 *
 * <p>GUEST is anonymous (identified only by cart token and handled outside UserAccount).
 * CUSTOMER is the default for registered users.
 * ADMIN has privileged operations but is explicitly denied shopping endpoints (§3.4).
 *
 * <p>New roles require an ADR. No Spring/JPA imports — satisfies domain_is_framework_free.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}

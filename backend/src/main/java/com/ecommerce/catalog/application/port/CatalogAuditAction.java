package com.ecommerce.catalog.application.port;

/**
 * Enumerates the admin actions in the catalog context that must be audit-logged
 * (security-architecture.md §6c). Maps 1:1 to {@code admin_audit_log.action_type} values.
 *
 * <p>Type-safe alternative to a raw {@code String action_type} at the port boundary — a typo in
 * an action name fails at compile time instead of producing an unqueryable audit row.
 */
public enum CatalogAuditAction {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_PRICE_CHANGED,
    PRODUCT_RETIRED,
    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    CATEGORY_DELETED
}

package com.ecommerce.inventory.application.port;

/**
 * Enumerates the admin actions in the inventory context that must be audit-logged
 * (security-architecture.md §6c). Maps 1:1 to {@code admin_audit_log.action_type} values.
 *
 * <p>Type-safe alternative to a raw {@code String action_type} at the port boundary — mirrors
 * catalog's {@code CatalogAuditAction}. Only one value in v1: the sole audited admin action in
 * this context's current scope is the manual stock adjustment endpoint.
 */
public enum InventoryAuditAction {
    STOCK_ADMIN_ADJUSTED
}

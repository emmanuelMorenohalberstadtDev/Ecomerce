package com.ecommerce.order.application.port;

/**
 * Enumerates the admin actions in the order context that must be audit-logged
 * (security-architecture.md §6c). Maps 1:1 to {@code admin_audit_log.action_type} values.
 *
 * <p>Type-safe alternative to a raw {@code String action_type} at the port boundary — mirrors
 * {@code inventory.application.port.InventoryAuditAction}. One value per admin lifecycle mutation;
 * customer-initiated cancellation ({@code CancelOrderUseCase}) does NOT write here — only the
 * {@code order_status_history} row, always written regardless of actor (security §6c is for admin
 * actions specifically).
 */
public enum OrderAuditAction {
    ORDER_ADMIN_MARKED_PAID,
    ORDER_ADMIN_CONFIRMED,
    ORDER_ADMIN_SHIPPED,
    ORDER_ADMIN_DELIVERED,
    ORDER_ADMIN_CANCELLED
}

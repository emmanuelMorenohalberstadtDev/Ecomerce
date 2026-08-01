package com.ecommerce.inventory.domain.port.out;

import com.ecommerce.inventory.domain.model.StockMovement;

/**
 * Domain port (outbound) for the append-only {@code stock_movements} ledger (database-design.md
 * §3.4/§3.7).
 *
 * <p><strong>Insert-only by construction:</strong> this interface exposes exactly one method — no
 * update, no delete, and deliberately no read either, mirroring catalog's {@code AuditLogPort}
 * ("there is deliberately no read method either ... nothing in the catalog context reads it
 * back"). Nothing in this task's scope reads {@code stock_movements} back (no admin ledger-review
 * endpoint is in scope — see the inventory handoff report); nothing here shape-permits an
 * {@code UPDATE}/{@code DELETE}, matching the DB grant (V004 §5: "SELECT, INSERT ONLY ... enforced
 * by the DB role itself") one layer up, in code.
 *
 * <p>Lives in the domain layer; implemented by {@code JpaStockMovementRepository} in
 * infrastructure.
 *
 * <p>No Spring/JPA imports — satisfies domain_is_framework_free and
 * {@code repository_interfaces_live_in_domain}.
 */
public interface StockMovementRepository {

    /** Appends one immutable ledger row. Must be called from within the same transaction as the audited stock change. */
    void record(StockMovement movement);
}

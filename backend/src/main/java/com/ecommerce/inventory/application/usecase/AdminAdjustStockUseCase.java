package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.application.port.AuditLogPort;
import com.ecommerce.inventory.application.port.CurrentActorPort;
import com.ecommerce.inventory.application.port.InventoryAuditAction;
import com.ecommerce.inventory.domain.exception.StockItemNotFoundException;
import com.ecommerce.inventory.domain.model.AdjustmentReason;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.shared.id.ProductId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies a manual admin correction to a product's available stock (domain-model.md §1 inventory:
 * "Admin stock adjustments live here (audited)"). Admin-only mutation, backing
 * {@code POST /api/v1/admin/stock-items/{productId}/adjustments}.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx. Enforced at the route
 * layer ({@code /api/v1/admin/**} requires {@code ADMIN}) and again here via {@link PreAuthorize}
 * (security-architecture §3.2).
 *
 * <p><strong>Requires an existing {@code stock_items} row</strong> — {@link StockItemNotFoundException}
 * (404) otherwise. Does not auto-create one; see that exception's javadoc and the inventory
 * handoff report for the open question this raises about how a product's very first stock row is
 * ever created.
 *
 * <p>Both writes — the {@code stock_movements} ledger row and the {@code admin_audit_log} row —
 * happen in this one {@code @Transactional} method, alongside the {@code stock_items} update
 * itself (security §6c requirement 3: an action without its audit row must not commit).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class AdminAdjustStockUseCase {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogPort auditLogPort;
    private final CurrentActorPort currentActorPort;
    private final Clock clock;

    public AdminAdjustStockUseCase(StockItemRepository stockItemRepository,
                                   StockMovementRepository stockMovementRepository,
                                   AuditLogPort auditLogPort,
                                   CurrentActorPort currentActorPort,
                                   Clock clock) {
        this.stockItemRepository = Objects.requireNonNull(stockItemRepository);
        this.stockMovementRepository = Objects.requireNonNull(stockMovementRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
        this.currentActorPort = Objects.requireNonNull(currentActorPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public StockItem execute(AdjustStockCommand command) {
        ProductId productId = ProductId.of(command.productId());
        StockItem stockItem = stockItemRepository.findById(productId)
                .orElseThrow(() -> new StockItemNotFoundException(
                        "No stock item exists for product " + productId));

        int quantityBefore = stockItem.getAvailable().value();
        AdjustmentReason reason = new AdjustmentReason(command.reason());
        stockItem.adjust(command.delta(), reason); // throws InsufficientStockException if result < 0
        StockItem saved = stockItemRepository.save(stockItem);

        UUID actorId = currentActorPort.currentActorId();
        Instant now = clock.instant();
        stockMovementRepository.record(new StockMovement(
                productId, MovementType.ADMIN_ADJUSTMENT, command.delta(), null, actorId,
                command.reason(), now));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("delta", command.delta());
        details.put("reason", command.reason());
        details.put("quantityBefore", quantityBefore);
        details.put("quantityAfter", saved.getAvailable().value());
        auditLogPort.record(InventoryAuditAction.STOCK_ADMIN_ADJUSTED, "StockItem",
                productId.toString(), details);

        return saved;
    }

    /**
     * @param productId target product's id (must already have a {@code stock_items} row)
     * @param delta      signed change to apply; positive = increase, negative = decrease
     * @param reason     mandatory free-form justification (audited)
     */
    public record AdjustStockCommand(String productId, int delta, String reason) {}
}

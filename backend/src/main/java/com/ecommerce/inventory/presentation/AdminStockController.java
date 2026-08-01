package com.ecommerce.inventory.presentation;

import com.ecommerce.inventory.application.usecase.AdminAdjustStockUseCase;
import com.ecommerce.inventory.application.usecase.AdminAdjustStockUseCase.AdjustStockCommand;
import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.inventory.presentation.dto.AdjustStockRequest;
import com.ecommerce.inventory.presentation.dto.StockItemResponse;
import com.ecommerce.inventory.presentation.mapper.StockItemMapper;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin manual stock correction: {@code /api/v1/admin/stock-items/**}. The route matcher in
 * {@code AuthSecurityConfiguration} already requires {@code ADMIN} at the coarse layer for
 * everything under {@code /api/v1/admin/**}; {@link AdminAdjustStockUseCase} additionally carries
 * {@code @PreAuthorize("hasRole('ADMIN')")} as the fine-grained second layer
 * (security-architecture §3.2). The mutation writes one row to the admin audit log
 * (security §6c) via inventory's own {@code AuditLogPort}, in the same transaction as the
 * {@code stock_items} update and the {@code stock_movements} ledger row.
 *
 * <p>This is the <strong>only</strong> REST surface this context exposes in this change.
 * Reserve/commit/release/expire-sweep are in-process ports for the future checkout context to
 * call directly — checkout has no module yet, so there is nothing to route those through, and
 * api-guidelines.md documents no public "check stock" read endpoint, so none is added here
 * either (see the inventory handoff report).
 *
 * <p>Auth decision table — this controller's only endpoint (no ownership dimension; admin ops
 * are role-gated only, security-architecture §3.4):
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th></tr>
 *   <tr><td>POST /admin/stock-items/{productId}/adjustments</td><td>401</td><td>403</td><td>200</td></tr>
 * </table>
 *
 * <p>Modeled as a created sub-resource via POST (api-guidelines §1.3: "state transitions are
 * sub-resources, created with POST") — an adjustment is a discrete, audited event against a
 * stock item, exactly like catalog's product retirement. Returns {@code 200} with the updated
 * {@link StockItemResponse}, not {@code 201} with a {@code Location} header, because the created
 * adjustment record itself has no independent read endpoint in this change (no admin ledger
 * screen is in scope — see the handoff report); this mirrors {@code AdminProductController
 * #retire}'s identical reasoning for the retirement sub-resource.
 */
@RestController
@RequestMapping("/api/v1/admin/stock-items")
@Validated
public class AdminStockController {

    private final AdminAdjustStockUseCase adminAdjustStockUseCase;

    public AdminStockController(AdminAdjustStockUseCase adminAdjustStockUseCase) {
        this.adminAdjustStockUseCase = adminAdjustStockUseCase;
    }

    @PostMapping("/{productId}/adjustments")
    public StockItemResponse adjust(@PathVariable UUID productId,
                                    @RequestBody @Valid AdjustStockRequest request) {
        StockItem stockItem = adminAdjustStockUseCase.execute(
                new AdjustStockCommand(productId.toString(), request.delta(), request.reason()));
        return StockItemMapper.toResponse(stockItem);
    }
}

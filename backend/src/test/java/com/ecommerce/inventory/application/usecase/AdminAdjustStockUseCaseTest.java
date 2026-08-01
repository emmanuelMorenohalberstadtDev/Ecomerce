package com.ecommerce.inventory.application.usecase;

import com.ecommerce.inventory.application.port.AuditLogPort;
import com.ecommerce.inventory.application.port.CurrentActorPort;
import com.ecommerce.inventory.application.port.InventoryAuditAction;
import com.ecommerce.inventory.application.usecase.AdminAdjustStockUseCase.AdjustStockCommand;
import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.ecommerce.inventory.domain.exception.StockItemNotFoundException;
import com.ecommerce.inventory.domain.model.MovementType;
import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.inventory.domain.model.StockMovement;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.inventory.domain.port.out.StockMovementRepository;
import com.ecommerce.shared.id.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminAdjustStockUseCase}.
 *
 * <p>Mocks: {@link StockItemRepository}, {@link StockMovementRepository}, {@link AuditLogPort},
 * {@link CurrentActorPort}. No Spring context — {@code @PreAuthorize} enforcement is a
 * method-security/AOP concern exercised only under a Spring context (out of scope for this unit
 * suite; not one of the required test cases).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AdminAdjustStockUseCaseTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private CurrentActorPort currentActorPort;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private AdminAdjustStockUseCase useCase;

    private final ProductId productId = ProductId.generate();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AdminAdjustStockUseCase(stockItemRepository, stockMovementRepository,
                auditLogPort, currentActorPort, clock);
    }

    private StockItem existingStockItem(int available) {
        return StockItem.reconstitute(productId, available, 0);
    }

    // ── admin adjustment (TC6) ────────────────────────────────────────────────

    @Test
    void shouldChangeQuantityAvailable_byDelta_TC6() {
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(existingStockItem(10)));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentActorPort.currentActorId()).thenReturn(actorId);

        StockItem result = useCase.execute(new AdjustStockCommand(productId.toString(), 5, "restock delivery"));

        assertThat(result.getAvailable().value()).isEqualTo(15);
    }

    @Test
    void shouldWriteAdminAdjustmentMovementRow_withActorIdAndReason_TC6() {
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(existingStockItem(10)));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentActorPort.currentActorId()).thenReturn(actorId);

        useCase.execute(new AdjustStockCommand(productId.toString(), -3, "damaged units removed"));

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).record(captor.capture());
        StockMovement movement = captor.getValue();
        assertThat(movement.movementType()).isEqualTo(MovementType.ADMIN_ADJUSTMENT);
        assertThat(movement.quantityDelta()).isEqualTo(-3);
        assertThat(movement.actorId()).isEqualTo(actorId);
        assertThat(movement.reason()).isEqualTo("damaged units removed");
        assertThat(movement.reservationId()).isNull();
        assertThat(movement.occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldWriteAuditLogEntry_inSameOperation_TC6() {
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(existingStockItem(10)));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentActorPort.currentActorId()).thenReturn(actorId);

        useCase.execute(new AdjustStockCommand(productId.toString(), 5, "restock delivery"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogPort).record(eq(InventoryAuditAction.STOCK_ADMIN_ADJUSTED), eq("StockItem"),
                eq(productId.toString()), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("delta", 5);
        assertThat(details).containsEntry("reason", "restock delivery");
        assertThat(details).containsEntry("quantityBefore", 10);
        assertThat(details).containsEntry("quantityAfter", 15);
    }

    // ── not found ──────────────────────────────────────────────────────────

    @Test
    void shouldThrowStockItemNotFoundException_whenNoStockItemExists() {
        when(stockItemRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AdjustStockCommand(productId.toString(), 5, "restock")))
                .isInstanceOf(StockItemNotFoundException.class);

        verify(stockItemRepository, never()).save(any());
        verify(stockMovementRepository, never()).record(any());
        verify(auditLogPort, never()).record(any(), any(), any(), any());
    }

    // ── negative-adjustment guard ──────────────────────────────────────────────

    @Test
    void shouldThrowInsufficientStockException_whenAdjustmentWouldDriveQuantityNegative() {
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(existingStockItem(5)));

        assertThatThrownBy(() -> useCase.execute(new AdjustStockCommand(productId.toString(), -10, "correction")))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockItemRepository, never()).save(any());
        verify(stockMovementRepository, never()).record(any());
        verify(auditLogPort, never()).record(any(), any(), any(), any());
    }
}

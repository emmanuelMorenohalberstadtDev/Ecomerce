package com.ecommerce.inventory.infrastructure.adapter;

import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.inventory.application.port.StockReservationPort.ReservationLine;
import com.ecommerce.inventory.application.port.StockReservationPort.ReservationOutcome;
import com.ecommerce.inventory.application.port.StockReservationPort.ReservationRequest;
import com.ecommerce.inventory.application.usecase.ReleaseReservationUseCase;
import com.ecommerce.inventory.application.usecase.ReserveStockUseCase;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link StockReservationAdapter} — translates
 * {@link ReserveStockUseCase}/{@link ReleaseReservationUseCase}'s domain exceptions (private to
 * inventory's own {@code domain.exception} package) into this façade's nested exception types.
 * Checkout is the first cross-context consumer of this façade, so its correctness is covered
 * directly here rather than only transitively through checkout's use-case tests.
 *
 * <p>Note on naming: inventory's own {@code domain.exception.InsufficientStockException}/
 * {@code InvalidReservationStateException} share simple names with this façade's nested exception
 * types of the same name — the two cannot both be imported unqualified in one file, so the
 * domain-layer ones (what {@link ReserveStockUseCase}/{@link ReleaseReservationUseCase} actually
 * throw) are referenced fully-qualified below; the façade-level ones (what the adapter must
 * translate them into) are imported via {@link StockReservationPort}.
 *
 * <p><strong>KNOWN PRODUCTION BUG (reported, not fixed — test-engineer has no production-code
 * write access):</strong> {@link #reserve_throwsInsufficientStockException_whenReserveStockUseCaseReportsShortage()}
 * and {@link #release_throwsInvalidReservationStateException_whenReservationIsNotHeld()} currently
 * FAIL against {@code StockReservationAdapter.java} lines 47 and 61. Both {@code catch} clauses
 * there use the bare simple name ({@code InsufficientStockException}/
 * {@code InvalidReservationStateException}), imported at the top of that file from
 * {@code inventory.domain.exception}. But {@code StockReservationAdapter implements
 * StockReservationPort}, and {@code StockReservationPort} itself declares nested classes with the
 * exact same simple names — per JLS member-type inheritance, those inherited nested types shadow
 * the single-type-imports for unqualified resolution inside the class body. The two {@code catch}
 * clauses therefore actually catch {@code StockReservationPort.InsufficientStockException}/
 * {@code StockReservationPort.InvalidReservationStateException} (the façade-level types), never the
 * domain-level ones {@link ReserveStockUseCase}/{@link ReleaseReservationUseCase} actually throw —
 * confirmed empirically by these two tests: the domain-level exception propagates straight through
 * {@code StockReservationAdapter} untranslated. This defeats the adapter's one job (ADR-0003
 * §Decision item 2: "a cross-context caller must never catch or import an inventory
 * {@code domain.exception} type") — checkout's {@code ReservationAdapter} would receive an
 * inventory {@code domain.exception} instance it cannot recognise, instead of the intended
 * {@code InsufficientStockForCheckoutException}/{@code InvalidCheckoutSessionStateException}.
 * Left failing deliberately rather than weakened to hide the bug (skill {@code junit}: "a test
 * that never fails is a liability"); the fix is to fully-qualify both {@code catch} clauses (or add
 * an import alias via fully-qualifying the domain exceptions) in production code, which is out of
 * this change's scope.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockReservationAdapterTest {

    @Mock
    private ReserveStockUseCase reserveStockUseCase;

    @Mock
    private ReleaseReservationUseCase releaseReservationUseCase;

    private StockReservationAdapter adapter;

    private final CheckoutSessionId checkoutSessionId = CheckoutSessionId.generate();
    private final ProductId productId = ProductId.generate();
    private final ReservationId reservationId = ReservationId.generate();

    @BeforeEach
    void setUp() {
        adapter = new StockReservationAdapter(reserveStockUseCase, releaseReservationUseCase);
    }

    private StockReservation heldReservation() {
        return StockReservation.reconstitute(reservationId, checkoutSessionId, ReservationStatus.HELD,
                Expiry.at(Instant.parse("2026-07-31T10:15:00Z")), Instant.parse("2026-07-31T10:00:00Z"),
                List.of(new ReservedLine(productId, Quantity.of(2))));
    }

    private ReservationRequest reservationRequest() {
        return new ReservationRequest(checkoutSessionId, List.of(new ReservationLine(productId, Quantity.of(2))),
                Instant.parse("2026-07-31T10:15:00Z"));
    }

    @Test
    void reserve_returnsMappedOutcome_whenReserveStockUseCaseSucceeds() {
        when(reserveStockUseCase.execute(any())).thenReturn(heldReservation());

        ReservationOutcome result = adapter.reserve(reservationRequest());

        assertThat(result.reservationId()).isEqualTo(reservationId);
        assertThat(result.lines()).extracting(ReservationLine::productId).containsExactly(productId);
    }

    @Test
    void reserve_throwsInsufficientStockException_whenReserveStockUseCaseReportsShortage() {
        when(reserveStockUseCase.execute(any()))
                .thenThrow(new com.ecommerce.inventory.domain.exception.InsufficientStockException("not enough stock"));

        assertThatThrownBy(() -> adapter.reserve(reservationRequest()))
                .isInstanceOf(StockReservationPort.InsufficientStockException.class);
    }

    @Test
    void release_delegatesToReleaseReservationUseCase() {
        adapter.release(reservationId);

        verify(releaseReservationUseCase).execute(reservationId);
    }

    @Test
    void release_throwsInvalidReservationStateException_whenReservationIsNotHeld() {
        doThrow(new com.ecommerce.inventory.domain.exception.InvalidReservationStateException("not HELD"))
                .when(releaseReservationUseCase).execute(reservationId);

        assertThatThrownBy(() -> adapter.release(reservationId))
                .isInstanceOf(StockReservationPort.InvalidReservationStateException.class);
    }
}

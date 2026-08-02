package com.ecommerce.inventory.infrastructure.adapter;

import com.ecommerce.inventory.application.port.StockReservationPort;
import com.ecommerce.inventory.application.port.StockReservationPort.ReservationLine;
import com.ecommerce.inventory.application.port.StockReservationPort.ReservationOutcome;
import com.ecommerce.inventory.application.port.StockReservationPort.ReservationRequest;
import com.ecommerce.inventory.application.usecase.CommitReservationUseCase;
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
 * {@link ReserveStockUseCase}/{@link ReleaseReservationUseCase}/{@link CommitReservationUseCase}'s
 * domain exceptions (private to inventory's own {@code domain.exception} package) into this
 * façade's nested exception types. Checkout and order are cross-context consumers of this façade,
 * so its correctness is covered directly here rather than only transitively through their use-case
 * tests.
 *
 * <p>Note on naming: inventory's own {@code domain.exception.InsufficientStockException}/
 * {@code InvalidReservationStateException} share simple names with this façade's nested exception
 * types of the same name — the two cannot both be imported unqualified in one file, so the
 * domain-layer ones (what {@link ReserveStockUseCase}/{@link ReleaseReservationUseCase}/
 * {@link CommitReservationUseCase} actually throw) are referenced fully-qualified below; the
 * façade-level ones (what the adapter must translate them into) are imported via
 * {@link StockReservationPort}.
 *
 * <p><strong>Correction to a prior revision of this javadoc:</strong> an earlier version of this
 * comment reported a "known production bug" — that {@code StockReservationAdapter}'s {@code catch}
 * clauses used unqualified simple names shadowed by {@link StockReservationPort}'s identically-named
 * nested exception types (JLS member-type inheritance), defeating the adapter's translation job.
 * Verified against the current {@code StockReservationAdapter.java}: all three {@code catch}
 * clauses ({@code reserve}, {@code release}, {@code commit}) already fully-qualify the domain
 * exception type being caught, which is immune to that shadowing hazard (qualification bypasses
 * unqualified-name resolution entirely) — the bug described no longer reproduces, confirmed
 * empirically by every test below passing. Left documented here, rather than silently deleted, so
 * a future edit that reintroduces an unqualified {@code catch} regresses visibly against this
 * suite instead of silently reopening the original hazard.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockReservationAdapterTest {

    @Mock
    private ReserveStockUseCase reserveStockUseCase;

    @Mock
    private ReleaseReservationUseCase releaseReservationUseCase;

    @Mock
    private CommitReservationUseCase commitReservationUseCase;

    private StockReservationAdapter adapter;

    private final CheckoutSessionId checkoutSessionId = CheckoutSessionId.generate();
    private final ProductId productId = ProductId.generate();
    private final ReservationId reservationId = ReservationId.generate();

    @BeforeEach
    void setUp() {
        adapter = new StockReservationAdapter(reserveStockUseCase, releaseReservationUseCase, commitReservationUseCase);
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

    // ── commit() — previously injected but entirely untested; order's
    //    AdminMarkOrderPaidUseCase is this method's first real cross-context caller ──

    @Test
    void commit_delegatesToCommitReservationUseCase() {
        when(commitReservationUseCase.execute(reservationId)).thenReturn(heldReservation());

        adapter.commit(reservationId);

        verify(commitReservationUseCase).execute(reservationId);
    }

    @Test
    void commit_throwsInvalidReservationStateException_whenReservationIsNotHeld() {
        doThrow(new com.ecommerce.inventory.domain.exception.InvalidReservationStateException("not HELD"))
                .when(commitReservationUseCase).execute(reservationId);

        assertThatThrownBy(() -> adapter.commit(reservationId))
                .isInstanceOf(StockReservationPort.InvalidReservationStateException.class);
    }
}

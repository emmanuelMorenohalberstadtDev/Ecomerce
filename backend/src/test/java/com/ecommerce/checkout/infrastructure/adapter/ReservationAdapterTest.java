package com.ecommerce.checkout.infrastructure.adapter;

import com.ecommerce.checkout.domain.exception.InsufficientStockForCheckoutException;
import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.port.out.ReservationPort.ReservationLine;
import com.ecommerce.checkout.domain.port.out.ReservationPort.ReservationOutcome;
import com.ecommerce.inventory.application.port.StockReservationPort;
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
 * Unit test for {@link ReservationAdapter} — translates inventory's façade-owned
 * {@link StockReservationPort} outcome/exceptions into checkout's own {@link ReservationOutcome}/
 * domain exceptions.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReservationAdapterTest {

    @Mock
    private StockReservationPort stockReservationPort;

    private ReservationAdapter adapter;

    private final CheckoutSessionId checkoutSessionId = CheckoutSessionId.generate();
    private final ProductId productId = ProductId.generate();
    private final ReservationId reservationId = ReservationId.generate();

    @BeforeEach
    void setUp() {
        adapter = new ReservationAdapter(stockReservationPort);
    }

    @Test
    void reserve_returnsMappedOutcome_whenStockReservationPortSucceeds() {
        when(stockReservationPort.reserve(any())).thenReturn(
                new StockReservationPort.ReservationOutcome(reservationId,
                        List.of(new StockReservationPort.ReservationLine(productId, Quantity.of(2)))));

        ReservationOutcome result = adapter.reserve(checkoutSessionId,
                List.of(new ReservationLine(productId, Quantity.of(2))), Instant.parse("2026-07-31T10:15:00Z"));

        assertThat(result.reservationId()).isEqualTo(reservationId);
    }

    @Test
    void reserve_throwsInsufficientStockForCheckoutException_whenStockReservationPortReportsShortage() {
        when(stockReservationPort.reserve(any()))
                .thenThrow(new StockReservationPort.InsufficientStockException("not enough stock"));

        assertThatThrownBy(() -> adapter.reserve(checkoutSessionId,
                List.of(new ReservationLine(productId, Quantity.of(2))), Instant.parse("2026-07-31T10:15:00Z")))
                .isInstanceOf(InsufficientStockForCheckoutException.class);
    }

    @Test
    void release_delegatesToStockReservationPort() {
        adapter.release(reservationId);

        verify(stockReservationPort).release(reservationId);
    }

    @Test
    void release_throwsInvalidCheckoutSessionStateException_whenReservationIsNotHeld() {
        doThrow(new StockReservationPort.InvalidReservationStateException("reservation not HELD"))
                .when(stockReservationPort).release(reservationId);

        assertThatThrownBy(() -> adapter.release(reservationId))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);
    }
}

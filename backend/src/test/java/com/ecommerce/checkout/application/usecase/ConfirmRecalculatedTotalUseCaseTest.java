package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.application.usecase.ConfirmRecalculatedTotalUseCase.ConfirmRecalculatedTotalCommand;
import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.checkout.domain.exception.CartNotCheckoutableException;
import com.ecommerce.checkout.domain.exception.CheckoutSessionNotFoundException;
import com.ecommerce.checkout.domain.exception.InvalidCheckoutSessionStateException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedLine;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.model.SessionStatus;
import com.ecommerce.checkout.domain.port.out.CartLinesPort;
import com.ecommerce.checkout.domain.port.out.CartLinesPort.CartLineView;
import com.ecommerce.checkout.domain.port.out.CartLinesPort.CartSnapshot;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.checkout.domain.port.out.PriceQuotePort;
import com.ecommerce.checkout.domain.port.out.ReservationPort;
import com.ecommerce.checkout.domain.port.out.ReservationPort.ReservationOutcome;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfirmRecalculatedTotalUseCase}.
 *
 * <p>Mocks: {@link CheckoutSessionRepository}, {@link CartLinesPort}, {@link PriceQuotePort},
 * {@link ReservationPort}, {@link ApplicationEventPublisher}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ConfirmRecalculatedTotalUseCaseTest {

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private CartLinesPort cartLinesPort;

    @Mock
    private PriceQuotePort priceQuotePort;

    @Mock
    private ReservationPort reservationPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);
    private final Duration paymentWindow = Duration.ofMinutes(15);

    private ConfirmRecalculatedTotalUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final CartId cartId = CartId.generate();
    private final CheckoutSessionId sessionId = CheckoutSessionId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new ConfirmRecalculatedTotalUseCase(checkoutSessionRepository, cartLinesPort, priceQuotePort,
                reservationPort, eventPublisher, clock, paymentWindow);
    }

    private static RecalculatedTotal totalOf(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        // Non-empty lines required: CheckoutAwaitingPaymentEvent.Line (ADR-0004) is built from
        // this in-memory RecalculatedTotal and its compact constructor rejects an empty list.
        RecalculatedLine line = new RecalculatedLine(ProductId.generate(), Quantity.of(1), money, money);
        return new RecalculatedTotal(List.of(line), money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private CheckoutSession pendingSession() {
        return CheckoutSession.start(sessionId, customerId, cartId, totalOf("50.00"),
                new Money(new BigDecimal("50.00"), "USD"), "idem-key-1", clock.instant());
    }

    private CartSnapshot cartWithOneLine() {
        return new CartSnapshot(cartId, List.of(new CartLineView(productId, Quantity.of(2))));
    }

    private ConfirmRecalculatedTotalCommand command(String confirmedTotal) {
        return new ConfirmRecalculatedTotalCommand(sessionId, customerId, new Money(new BigDecimal(confirmedTotal), "USD"));
    }

    // ── happy path: fresh total matches what the customer is confirming ────

    @Test
    void shouldReturnConfirmedOutcome_andReserveStock_whenFreshTotalMatchesConfirmedTotal() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(pendingSession()));
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        when(priceQuotePort.recalculate(any())).thenReturn(totalOf("50.00"));
        ReservationId reservationId = ReservationId.generate();
        when(reservationPort.reserve(any(), any(), any())).thenReturn(new ReservationOutcome(reservationId));
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutOutcome outcome = useCase.execute(command("50.00"));

        assertThat(outcome).isInstanceOf(CheckoutOutcome.Confirmed.class);
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.AWAITING_PAYMENT);
        assertThat(outcome.session().getReservationId()).isEqualTo(reservationId);
    }

    @Test
    void shouldPublishCheckoutAwaitingPaymentEvent_whenFreshTotalMatchesConfirmedTotal() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(pendingSession()));
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        RecalculatedTotal total = totalOf("50.00");
        when(priceQuotePort.recalculate(any())).thenReturn(total);
        when(reservationPort.reserve(any(), any(), any()))
                .thenReturn(new ReservationOutcome(ReservationId.generate()));
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command("50.00"));

        ArgumentCaptor<CheckoutAwaitingPaymentEvent> captor = ArgumentCaptor.forClass(CheckoutAwaitingPaymentEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CheckoutAwaitingPaymentEvent event = captor.getValue();
        // order's PlaceOrderFromCheckoutUseCase builds its LineSnapshots/OrderTotals directly from
        // these fields (ADR-0004 item 1) — assert them explicitly, mirroring
        // StartCheckoutUseCaseTest's equivalent assertion for the sibling reservation path.
        assertThat(event.lines()).hasSize(1);
        assertThat(event.lines().get(0).productId()).isEqualTo(total.lines().get(0).productId());
        assertThat(event.discountTotal()).isEqualTo(total.discountTotal());
        assertThat(event.shippingTotal()).isEqualTo(total.shippingFee());
        assertThat(event.grandTotal()).isEqualTo(total.grandTotal());
    }

    // ── price moved again: stays PENDING_CONFIRMATION, does not loop ───────

    @Test
    void shouldReturnNeedsReconfirmationOutcome_andPersistRefreshedTotal_whenPriceMovedAgain() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(pendingSession()));
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        RecalculatedTotal freshTotal = totalOf("65.00");
        when(priceQuotePort.recalculate(any())).thenReturn(freshTotal);
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutOutcome outcome = useCase.execute(command("50.00"));

        assertThat(outcome).isInstanceOf(CheckoutOutcome.NeedsReconfirmation.class);
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.PENDING_CONFIRMATION);
        assertThat(outcome.session().getRecalculatedTotal()).isEqualTo(freshTotal);
        verifyNoInteractions(reservationPort);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldPersistTheRefreshedSession_whenPriceMovedAgain() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(pendingSession()));
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        RecalculatedTotal freshTotal = totalOf("65.00");
        when(priceQuotePort.recalculate(any())).thenReturn(freshTotal);
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command("50.00"));

        ArgumentCaptor<CheckoutSession> captor = ArgumentCaptor.forClass(CheckoutSession.class);
        verify(checkoutSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getRecalculatedTotal()).isEqualTo(freshTotal);
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.PENDING_CONFIRMATION);
    }

    // ── ownership / not found ──────────────────────────────────────────────

    @Test
    void shouldThrowCheckoutSessionNotFoundException_whenSessionDoesNotExistOrBelongsToSomeoneElse() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(CheckoutSessionNotFoundException.class);

        verifyNoInteractions(cartLinesPort, priceQuotePort, reservationPort);
    }

    // ── invalid state ──────────────────────────────────────────────────────

    @Test
    void shouldThrowInvalidCheckoutSessionStateException_whenSessionIsNotPendingConfirmation() {
        CheckoutSession awaitingPayment = pendingSession();
        awaitingPayment.moveToAwaitingPayment(ReservationId.generate(), clock.instant().plus(paymentWindow));
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(awaitingPayment));

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(InvalidCheckoutSessionStateException.class);

        verifyNoInteractions(cartLinesPort, priceQuotePort, reservationPort);
    }

    // ── cart no longer checkoutable ─────────────────────────────────────────

    @Test
    void shouldThrowCartNotCheckoutableException_whenCustomerNoLongerHasAnActiveCart() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(pendingSession()));
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(CartNotCheckoutableException.class);

        verifyNoInteractions(priceQuotePort, reservationPort);
    }
}

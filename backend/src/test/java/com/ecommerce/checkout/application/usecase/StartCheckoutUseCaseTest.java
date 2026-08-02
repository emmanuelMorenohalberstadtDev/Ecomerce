package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.application.usecase.StartCheckoutUseCase.StartCheckoutCommand;
import com.ecommerce.checkout.domain.event.CheckoutAwaitingPaymentEvent;
import com.ecommerce.checkout.domain.exception.CartNotCheckoutableException;
import com.ecommerce.checkout.domain.exception.CheckoutSessionAlreadyOpenException;
import com.ecommerce.checkout.domain.exception.DuplicateIdempotencyKeyException;
import com.ecommerce.checkout.domain.exception.InsufficientStockForCheckoutException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StartCheckoutUseCase}.
 *
 * <p>Mocks: {@link CartLinesPort}, {@link PriceQuotePort}, {@link ReservationPort},
 * {@link CheckoutSessionRepository}, {@link ApplicationEventPublisher}. No Spring context. The
 * class is constructed directly (not {@code @Service}-annotated — see its javadoc), so the fixed
 * payment-window {@link Duration} is handed straight to the constructor.
 *
 * <p>Two distinct guards run before any cart/price/reservation work: the open-session guard
 * ({@link CheckoutSessionAlreadyOpenException}) and the idempotency-key guard
 * ({@link DuplicateIdempotencyKeyException}). Per the use case's javadoc the open-session guard
 * runs first — {@link #shouldThrowCheckoutSessionAlreadyOpenException_beforeCheckingIdempotencyKey()}
 * asserts that ordering directly.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StartCheckoutUseCaseTest {

    @Mock
    private CartLinesPort cartLinesPort;

    @Mock
    private PriceQuotePort priceQuotePort;

    @Mock
    private ReservationPort reservationPort;

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);
    private final Duration paymentWindow = Duration.ofMinutes(15);

    private StartCheckoutUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final CartId cartId = CartId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new StartCheckoutUseCase(cartLinesPort, priceQuotePort, reservationPort,
                checkoutSessionRepository, eventPublisher, clock, paymentWindow);
    }

    /** Stubs both up-front guards to "clear" — used by every test that expects execute() to proceed past them. */
    private void noOpenSessionAndNoReusedKey() {
        when(checkoutSessionRepository.findOpenByCustomerId(customerId)).thenReturn(Optional.empty());
        when(checkoutSessionRepository.findByCustomerIdAndIdempotencyKey(eq(customerId), any()))
                .thenReturn(Optional.empty());
    }

    private CartSnapshot cartWithOneLine() {
        return new CartSnapshot(cartId, List.of(new CartLineView(productId, Quantity.of(2))));
    }

    private static RecalculatedTotal totalOf(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        return new RecalculatedTotal(List.of(), money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private StartCheckoutCommand command(String expectedTotal) {
        return new StartCheckoutCommand(customerId, "idem-key-1", new Money(new BigDecimal(expectedTotal), "USD"));
    }

    // ── happy path: totals match -> reserve and confirm ───────────────────

    @Test
    void shouldReturnConfirmedOutcome_andReserveStock_whenRecalculatedTotalMatchesExpected() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        when(priceQuotePort.recalculate(any())).thenReturn(totalOf("50.00"));
        when(checkoutSessionRepository.create(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        ReservationId reservationId = ReservationId.generate();
        when(reservationPort.reserve(any(), any(), any())).thenReturn(new ReservationOutcome(reservationId));
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutOutcome outcome = useCase.execute(command("50.00"));

        assertThat(outcome).isInstanceOf(CheckoutOutcome.Confirmed.class);
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.AWAITING_PAYMENT);
        assertThat(outcome.session().getReservationId()).isEqualTo(reservationId);
    }

    @Test
    void shouldReserveStockForDeadlineOfNowPlusPaymentWindow_whenTotalsMatch() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        when(priceQuotePort.recalculate(any())).thenReturn(totalOf("50.00"));
        when(checkoutSessionRepository.create(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationPort.reserve(any(), any(), any()))
                .thenReturn(new ReservationOutcome(ReservationId.generate()));
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command("50.00"));

        verify(reservationPort).reserve(any(), any(), eq(clock.instant().plus(paymentWindow)));
    }

    @Test
    void shouldPublishCheckoutAwaitingPaymentEvent_whenTotalsMatch() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        when(priceQuotePort.recalculate(any())).thenReturn(totalOf("50.00"));
        when(checkoutSessionRepository.create(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        ReservationId reservationId = ReservationId.generate();
        when(reservationPort.reserve(any(), any(), any())).thenReturn(new ReservationOutcome(reservationId));
        when(checkoutSessionRepository.save(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutOutcome outcome = useCase.execute(command("50.00"));

        ArgumentCaptor<CheckoutAwaitingPaymentEvent> captor = ArgumentCaptor.forClass(CheckoutAwaitingPaymentEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CheckoutAwaitingPaymentEvent event = captor.getValue();
        assertThat(event.checkoutSessionId()).isEqualTo(outcome.session().getId());
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.cartId()).isEqualTo(cartId);
        assertThat(event.reservationId()).isEqualTo(reservationId);
    }

    // ── price mismatch: result, not exception ─────────────────────────────

    @Test
    void shouldReturnNeedsReconfirmationOutcome_withoutReservingStock_whenRecalculatedTotalDiffersFromExpected() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        when(priceQuotePort.recalculate(any())).thenReturn(totalOf("55.00"));
        when(checkoutSessionRepository.create(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutOutcome outcome = useCase.execute(command("50.00"));

        assertThat(outcome).isInstanceOf(CheckoutOutcome.NeedsReconfirmation.class);
        assertThat(outcome.session().getStatus()).isEqualTo(SessionStatus.PENDING_CONFIRMATION);
        verifyNoInteractions(reservationPort);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── empty / missing cart ───────────────────────────────────────────────

    @Test
    void shouldThrowCartNotCheckoutableException_whenCustomerHasNoActiveCart() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(CartNotCheckoutableException.class);

        verifyNoInteractions(priceQuotePort, reservationPort);
        verify(checkoutSessionRepository, never()).create(any());
    }

    @Test
    void shouldThrowCartNotCheckoutableException_whenActiveCartHasNoLines() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId))
                .thenReturn(Optional.of(new CartSnapshot(cartId, List.of())));

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(CartNotCheckoutableException.class);

        verifyNoInteractions(priceQuotePort, reservationPort);
    }

    // ── open-session guard vs. idempotency-key guard (security review gate) ──

    @Test
    void shouldThrowCheckoutSessionAlreadyOpenException_whenCustomerAlreadyHasAnOpenSession() {
        CheckoutSession openSession = CheckoutSession.start(CheckoutSessionId.generate(),
                customerId, cartId, totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "other-key",
                clock.instant());
        when(checkoutSessionRepository.findOpenByCustomerId(customerId)).thenReturn(Optional.of(openSession));

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(CheckoutSessionAlreadyOpenException.class);
    }

    @Test
    void shouldThrowCheckoutSessionAlreadyOpenException_beforeCheckingIdempotencyKey() {
        CheckoutSession openSession = CheckoutSession.start(CheckoutSessionId.generate(),
                customerId, cartId, totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "other-key",
                clock.instant());
        when(checkoutSessionRepository.findOpenByCustomerId(customerId)).thenReturn(Optional.of(openSession));

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(CheckoutSessionAlreadyOpenException.class);

        verify(checkoutSessionRepository, never()).findByCustomerIdAndIdempotencyKey(any(), any());
        verifyNoInteractions(cartLinesPort, priceQuotePort, reservationPort);
    }

    @Test
    void shouldThrowDuplicateIdempotencyKeyException_whenKeyAlreadyUsed_andNoOpenSessionExists() {
        CheckoutSession priorSession = CheckoutSession.start(CheckoutSessionId.generate(),
                customerId, cartId, totalOf("50.00"), new Money(new BigDecimal("50.00"), "USD"), "idem-key-1",
                clock.instant());
        when(checkoutSessionRepository.findByCustomerIdAndIdempotencyKey(customerId, "idem-key-1"))
                .thenReturn(Optional.of(priorSession));

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);

        verifyNoInteractions(cartLinesPort, priceQuotePort, reservationPort);
    }

    // ── insufficient stock propagation ─────────────────────────────────────

    @Test
    void shouldPropagateInsufficientStockForCheckoutException_whenReservationPortCannotReserve() {
        noOpenSessionAndNoReusedKey();
        when(cartLinesPort.findActiveCartLines(customerId)).thenReturn(Optional.of(cartWithOneLine()));
        when(priceQuotePort.recalculate(any())).thenReturn(totalOf("50.00"));
        when(checkoutSessionRepository.create(any(CheckoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationPort.reserve(any(), any(), any()))
                .thenThrow(new InsufficientStockForCheckoutException("not enough stock"));

        assertThatThrownBy(() -> useCase.execute(command("50.00")))
                .isInstanceOf(InsufficientStockForCheckoutException.class);

        verify(checkoutSessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── command validation ─────────────────────────────────────────────────

    @Test
    void command_throwsNullPointerException_whenCustomerIdIsNull() {
        assertThatThrownBy(() -> new StartCheckoutCommand(null, "key", new Money(new BigDecimal("1.00"), "USD")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void command_throwsIllegalArgumentException_whenIdempotencyKeyIsBlank() {
        assertThatThrownBy(() -> new StartCheckoutCommand(customerId, "  ", new Money(new BigDecimal("1.00"), "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void command_throwsNullPointerException_whenExpectedTotalIsNull() {
        assertThatThrownBy(() -> new StartCheckoutCommand(customerId, "key", null))
                .isInstanceOf(NullPointerException.class);
    }
}

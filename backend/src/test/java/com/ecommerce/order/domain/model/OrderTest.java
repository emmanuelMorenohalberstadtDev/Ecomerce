package com.ecommerce.order.domain.model;

import com.ecommerce.order.domain.exception.InvalidOrderTransitionException;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Order} aggregate root — the order status state machine, its
 * immutable purchase snapshot, and reconstitution. No Spring context, mirroring
 * {@code checkout.domain.model.CheckoutSessionTest}'s style for the sibling aggregate.
 */
@Tag("unit")
class OrderTest {

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-31T12:30:00Z");

    private final CustomerId customerId = CustomerId.generate();
    private final ReservationId reservationId = ReservationId.generate();

    private static List<LineSnapshot> oneLine() {
        Money unitPrice = new Money(new BigDecimal("10.00"), "USD");
        return List.of(new LineSnapshot(ProductId.generate(), "Widget", unitPrice, Quantity.of(2)));
    }

    private static OrderTotals totals(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        return new OrderTotals(money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private Order placedOrder() {
        return Order.place(OrderId.generate(), customerId, oneLine(), totals("20.00"), null,
                reservationId, Actor.system(), NOW);
    }

    private Order paidOrder() {
        Order order = placedOrder();
        order.markPaid(Actor.admin(null), LATER);
        return order;
    }

    private Order confirmedOrder() {
        Order order = paidOrder();
        order.confirm(Actor.admin(null), LATER);
        return order;
    }

    private Order shippedOrder() {
        Order order = confirmedOrder();
        order.ship(Actor.admin(null), LATER);
        return order;
    }

    private Order deliveredOrder() {
        Order order = shippedOrder();
        order.deliver(Actor.admin(null), LATER);
        return order;
    }

    private Order cancelledOrder() {
        Order order = placedOrder();
        order.cancel(Actor.customer(customerId.value()), LATER);
        return order;
    }

    private Order failedOrder() {
        Order order = placedOrder();
        order.fail(Actor.system(), LATER);
        return order;
    }

    // ── place() factory ─────────────────────────────────────────────────────

    @Test
    void place_producesPlacedStatus_withInitialNullToPlacedTransition() {
        Order order = placedOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getStatusHistory()).hasSize(1);
        OrderStatusTransition initial = order.getStatusHistory().get(0);
        assertThat(initial.from()).isNull();
        assertThat(initial.to()).isEqualTo(OrderStatus.PLACED);
        assertThat(initial.actor()).isEqualTo(Actor.system());
        assertThat(initial.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void place_setsVersionToZeroAndPlacedAtToNow() {
        Order order = placedOrder();

        assertThat(order.getVersion()).isZero();
        assertThat(order.getPlacedAt()).isEqualTo(NOW);
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void place_storesCustomerIdReservationIdAndTotals() {
        Order order = placedOrder();

        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getReservationId()).isEqualTo(reservationId);
        assertThat(order.getTotals()).isEqualTo(totals("20.00"));
        assertThat(order.getCouponCode()).isNull();
    }

    @Test
    void place_throwsIllegalArgumentException_whenIdIsNull() {
        assertThatThrownBy(() -> Order.place(null, customerId, oneLine(), totals("20.00"), null,
                reservationId, Actor.system(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_throwsIllegalArgumentException_whenCustomerIdIsNull() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), null, oneLine(), totals("20.00"), null,
                reservationId, Actor.system(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_throwsIllegalArgumentException_whenLinesIsNull() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), customerId, null, totals("20.00"), null,
                reservationId, Actor.system(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_throwsIllegalArgumentException_whenLinesIsEmpty() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), customerId, List.of(), totals("20.00"), null,
                reservationId, Actor.system(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_throwsIllegalArgumentException_whenTotalsIsNull() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), customerId, oneLine(), null, null,
                reservationId, Actor.system(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_throwsIllegalArgumentException_whenReservationIdIsNull() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), customerId, oneLine(), totals("20.00"), null,
                null, Actor.system(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_throwsNullPointerException_whenActorIsNull() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), customerId, oneLine(), totals("20.00"), null,
                reservationId, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void place_throwsNullPointerException_whenNowIsNull() {
        assertThatThrownBy(() -> Order.place(OrderId.generate(), customerId, oneLine(), totals("20.00"), null,
                reservationId, Actor.system(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── immutability: lines/statusHistory are defensively copied and unmodifiable ──

    @Test
    void getLines_returnsUnmodifiableView() {
        Order order = placedOrder();

        List<LineSnapshot> lines = order.getLines();

        assertThatThrownBy(() -> lines.add(lines.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getLines_isUnaffectedByMutatingTheOriginalListPassedToPlace() {
        List<LineSnapshot> mutableLines = new ArrayList<>(oneLine());
        Order order = Order.place(OrderId.generate(), customerId, mutableLines, totals("20.00"), null,
                reservationId, Actor.system(), NOW);

        mutableLines.add(new LineSnapshot(ProductId.generate(), "Extra", new Money(new BigDecimal("5.00"), "USD"),
                Quantity.of(1)));

        assertThat(order.getLines()).hasSize(1);
    }

    @Test
    void getLines_isUnaffectedByMutatingAPreviouslyReturnedView() {
        Order order = placedOrder();
        List<LineSnapshot> firstView = new ArrayList<>(order.getLines());
        firstView.clear();

        assertThat(order.getLines()).hasSize(1);
    }

    @Test
    void getStatusHistory_returnsUnmodifiableView() {
        Order order = placedOrder();

        List<OrderStatusTransition> history = order.getStatusHistory();

        assertThatThrownBy(() -> history.add(history.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getStatusHistory_growsByExactlyOnePerTransition_andIsNotAffectedByExternalMutation() {
        Order order = placedOrder();
        order.markPaid(Actor.admin(null), LATER);

        List<OrderStatusTransition> mutableCopy = new ArrayList<>(order.getStatusHistory());
        mutableCopy.clear();

        assertThat(order.getStatusHistory()).hasSize(2);
    }

    // ── legal transitions ─────────────────────────────────────────────────

    @Test
    void markPaid_transitionsFromPlacedToPaid_andAppendsTransition() {
        Order order = placedOrder();

        order.markPaid(Actor.admin(null), LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        OrderStatusTransition latest = order.getLatestTransition();
        assertThat(latest.from()).isEqualTo(OrderStatus.PLACED);
        assertThat(latest.to()).isEqualTo(OrderStatus.PAID);
        assertThat(latest.occurredAt()).isEqualTo(LATER);
        assertThat(order.getStatusHistory()).hasSize(2);
    }

    @Test
    void confirm_transitionsFromPaidToConfirmed_andAppendsTransition() {
        Order order = paidOrder();

        order.confirm(Actor.admin(null), LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getLatestTransition().from()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getLatestTransition().to()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void ship_transitionsFromConfirmedToShipped_andAppendsTransition() {
        Order order = confirmedOrder();

        order.ship(Actor.admin(null), LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getLatestTransition().from()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getLatestTransition().to()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void deliver_transitionsFromShippedToDelivered_andAppendsTransition() {
        Order order = shippedOrder();

        order.deliver(Actor.admin(null), LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getLatestTransition().from()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getLatestTransition().to()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void fail_transitionsFromPlacedToFailed_andAppendsTransition() {
        Order order = placedOrder();

        order.fail(Actor.system(), LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getLatestTransition().from()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getLatestTransition().to()).isEqualTo(OrderStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PLACED", "PAID", "CONFIRMED"})
    void cancel_transitionsFromAnyOfPlacedPaidConfirmed_toCancelled(OrderStatus fromStatus) {
        Order order = orderIn(fromStatus);

        order.cancel(Actor.customer(customerId.value()), LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getLatestTransition().from()).isEqualTo(fromStatus);
        assertThat(order.getLatestTransition().to()).isEqualTo(OrderStatus.CANCELLED);
    }

    private Order orderIn(OrderStatus status) {
        return switch (status) {
            case PLACED -> placedOrder();
            case PAID -> paidOrder();
            case CONFIRMED -> confirmedOrder();
            default -> throw new IllegalArgumentException("Unsupported fixture status: " + status);
        };
    }

    // ── illegal transitions ───────────────────────────────────────────────

    @Test
    void ship_throwsInvalidOrderTransitionException_whenStillPlaced_skippingPaidAndConfirmed() {
        Order order = placedOrder();

        assertThatThrownBy(() -> order.ship(Actor.admin(null), LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void cancel_throwsInvalidOrderTransitionException_whenAlreadyShipped() {
        Order order = shippedOrder();

        assertThatThrownBy(() -> order.cancel(Actor.customer(customerId.value()), LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransitionCall.class)
    void everyTransition_throwsInvalidOrderTransitionException_whenAlreadyDelivered(TransitionCall call) {
        Order order = deliveredOrder();

        assertThatThrownBy(() -> call.apply(order, LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransitionCall.class)
    void everyTransition_throwsInvalidOrderTransitionException_whenAlreadyCancelled(TransitionCall call) {
        Order order = cancelledOrder();

        assertThatThrownBy(() -> call.apply(order, LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransitionCall.class)
    void everyTransition_throwsInvalidOrderTransitionException_whenAlreadyFailed(TransitionCall call) {
        Order order = failedOrder();

        assertThatThrownBy(() -> call.apply(order, LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void markPaid_throwsInvalidOrderTransitionException_whenAlreadyPaid() {
        Order order = paidOrder();

        assertThatThrownBy(() -> order.markPaid(Actor.admin(null), LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void fail_throwsInvalidOrderTransitionException_whenAlreadyPaid() {
        Order order = paidOrder();

        assertThatThrownBy(() -> order.fail(Actor.system(), LATER))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    /** Every named domain transition method, for exhaustive "already terminal" parameterized checks. */
    private enum TransitionCall {
        MARK_PAID((order, now) -> order.markPaid(Actor.admin(null), now)),
        CONFIRM((order, now) -> order.confirm(Actor.admin(null), now)),
        SHIP((order, now) -> order.ship(Actor.admin(null), now)),
        DELIVER((order, now) -> order.deliver(Actor.admin(null), now)),
        CANCEL((order, now) -> order.cancel(Actor.admin(null), now)),
        FAIL((order, now) -> order.fail(Actor.system(), now));

        private final BiConsumer<Order, Instant> action;

        TransitionCall(BiConsumer<Order, Instant> action) {
            this.action = action;
        }

        void apply(Order order, Instant now) {
            action.accept(order, now);
        }
    }

    // ── reconstitute() ────────────────────────────────────────────────────

    @Test
    void reconstitute_restoresExactState() {
        OrderId id = OrderId.generate();
        List<LineSnapshot> lines = oneLine();
        OrderTotals total = totals("20.00");
        OrderStatusTransition initial = new OrderStatusTransition(null, OrderStatus.PLACED, Actor.system(), NOW);
        Instant updatedAt = LATER;

        Order order = Order.reconstitute(id, customerId, OrderStatus.PLACED, lines, total, "SAVE10",
                reservationId, List.of(initial), 3L, NOW, updatedAt);

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getLines()).isEqualTo(lines);
        assertThat(order.getTotals()).isEqualTo(total);
        assertThat(order.getCouponCode()).isEqualTo("SAVE10");
        assertThat(order.getReservationId()).isEqualTo(reservationId);
        assertThat(order.getStatusHistory()).containsExactly(initial);
        assertThat(order.getVersion()).isEqualTo(3L);
        assertThat(order.getPlacedAt()).isEqualTo(NOW);
        assertThat(order.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenVersionIsNegative() {
        OrderStatusTransition initial = new OrderStatusTransition(null, OrderStatus.PLACED, Actor.system(), NOW);

        assertThatThrownBy(() -> Order.reconstitute(OrderId.generate(), customerId, OrderStatus.PLACED, oneLine(),
                totals("20.00"), null, reservationId, List.of(initial), -1L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenStatusHistoryIsEmpty() {
        assertThatThrownBy(() -> Order.reconstitute(OrderId.generate(), customerId, OrderStatus.PLACED, oneLine(),
                totals("20.00"), null, reservationId, List.of(), 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── equals/hashCode ────────────────────────────────────────────────────

    @Test
    void equals_isBasedOnId() {
        OrderId id = OrderId.generate();
        OrderStatusTransition initial = new OrderStatusTransition(null, OrderStatus.PLACED, Actor.system(), NOW);
        Order a = Order.reconstitute(id, customerId, OrderStatus.PLACED, oneLine(), totals("20.00"), null,
                reservationId, List.of(initial), 0L, NOW, NOW);
        Order b = Order.reconstitute(id, CustomerId.generate(), OrderStatus.PAID, oneLine(), totals("99.00"), "X",
                ReservationId.generate(), List.of(initial), 5L, NOW, LATER);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void equals_returnsFalse_whenIdsDiffer() {
        Order a = placedOrder();
        Order b = placedOrder();

        assertThat(a).isNotEqualTo(b);
    }
}

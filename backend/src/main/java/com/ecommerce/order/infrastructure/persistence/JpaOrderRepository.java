package com.ecommerce.order.infrastructure.persistence;

import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.exception.DuplicateOrderException;
import com.ecommerce.order.domain.exception.OrderConcurrentModificationException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.ActorType;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.OrderStatusTransition;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter implementing the domain's {@link OrderRepository} port.
 *
 * <p>Uses three independent Spring Data DAOs ({@link SpringDataOrderDao},
 * {@link SpringDataOrderItemDao}, {@link SpringDataOrderStatusHistoryDao}) rather than one JPA
 * entity graph with mapped {@code @OneToMany} collections — see {@link OrderJpaEntity}'s class
 * javadoc for why. {@link #create} writes all three tables once; {@link #save} (used for every
 * subsequent status transition) writes only the header and exactly one new history row, leaving
 * {@code order_items} and every earlier history row untouched.
 *
 * <p><strong>Point-read query budget</strong> (backend-architecture §7 perf budget: "point read
 * ≤ 3 statements"): {@link #findById}/{@link #findByIdAndCustomerId}/{@link #findByReservationId}
 * each issue exactly 3 queries (header, items, history) rather than a single join-fetch — combining
 * two independent one-to-many collections ({@code order_items} and {@code order_status_history})
 * in one JPQL join-fetch query is the well-known "cannot simultaneously fetch multiple bags"
 * problem (Cartesian product row duplication across the two collections); three targeted queries
 * is the correct, established fix, not a shortcut (database-design.md §7: "order detail = order +
 * items + history in one query" is honoured in spirit — one round trip per concern — not literally
 * as a single SQL statement).
 *
 * <p>Translates vendor exceptions into order's own domain exceptions at the boundary (hexagonal
 * skill): a lost {@code uq_orders_reservation} race on {@link #create}
 * ({@link DataIntegrityViolationException}) becomes {@link DuplicateOrderException} (409); a lost
 * optimistic-lock race on {@link #save} ({@link ObjectOptimisticLockingFailureException}) becomes
 * {@link OrderConcurrentModificationException} (409) — mirrors
 * {@code JpaCheckoutSessionRepository} exactly.
 */
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderDao springDataOrderDao;
    private final SpringDataOrderItemDao springDataOrderItemDao;
    private final SpringDataOrderStatusHistoryDao springDataOrderStatusHistoryDao;

    public JpaOrderRepository(SpringDataOrderDao springDataOrderDao,
                              SpringDataOrderItemDao springDataOrderItemDao,
                              SpringDataOrderStatusHistoryDao springDataOrderStatusHistoryDao) {
        this.springDataOrderDao = Objects.requireNonNull(springDataOrderDao);
        this.springDataOrderItemDao = Objects.requireNonNull(springDataOrderItemDao);
        this.springDataOrderStatusHistoryDao = Objects.requireNonNull(springDataOrderStatusHistoryDao);
    }

    @Override
    public Order create(Order order) {
        OrderJpaEntity saved;
        try {
            saved = springDataOrderDao.save(toHeaderEntity(order));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateOrderException(
                    "An order already exists for reservation " + order.getReservationId());
        }

        List<OrderItemJpaEntity> items = order.getLines().stream()
                .map(l -> new OrderItemJpaEntity(saved.getId(), l.productId().value(), l.productName(),
                        l.unitPrice().amount(), l.unitPrice().currency(), l.quantity().value()))
                .toList();
        springDataOrderItemDao.saveAll(items);

        OrderStatusTransition initial = order.getStatusHistory().get(0);
        springDataOrderStatusHistoryDao.save(toHistoryEntity(saved.getId(), initial));

        return toDomain(saved, order.getLines(), order.getStatusHistory());
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity saved;
        try {
            saved = springDataOrderDao.save(toHeaderEntity(order));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OrderConcurrentModificationException(
                    "Order " + order.getId() + " was modified concurrently; reload and retry", e);
        }

        // Exactly the one newest transition — every earlier row already persisted, never re-inserted.
        springDataOrderStatusHistoryDao.save(toHistoryEntity(saved.getId(), order.getLatestTransition()));

        // Lines are write-once at create() and never change afterward (rule 3) — the in-memory
        // list on `order` already reflects the persisted rows exactly, no re-query needed.
        return toDomain(saved, order.getLines(), order.getStatusHistory());
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return springDataOrderDao.findById(id.value()).map(this::toDomainFull);
    }

    @Override
    public Optional<Order> findByIdAndCustomerId(OrderId id, CustomerId customerId) {
        return springDataOrderDao.findByIdAndCustomerId(id.value(), customerId.value()).map(this::toDomainFull);
    }

    @Override
    public Optional<Order> findByReservationId(ReservationId reservationId) {
        return springDataOrderDao.findByReservationId(reservationId.value()).map(this::toDomainFull);
    }

    @Override
    public PageResult<OrderSummary> findByCustomerId(CustomerId customerId, int page, int size) {
        Page<OrderJpaEntity> result = springDataOrderDao.findByCustomerIdOrderByPlacedAtDesc(
                customerId.value(), PageRequest.of(page, size));

        List<OrderSummary> content = result.getContent().stream()
                .map(e -> new OrderSummary(OrderId.of(e.getId()), e.getStatus(),
                        new Money(e.getGrandTotal(), e.getCurrency()), e.getPlacedAt()))
                .toList();

        return new PageResult<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private OrderJpaEntity toHeaderEntity(Order order) {
        OrderTotals totals = order.getTotals();
        return new OrderJpaEntity(
                order.getId().value(),
                order.getCustomerId().value(),
                order.getStatus().name(),
                totals.itemsTotal().amount(),
                totals.discountTotal().amount(),
                totals.shippingTotal().amount(),
                totals.grandTotal().amount(),
                totals.currency(),
                order.getCouponCode(),
                order.getReservationId().value(),
                order.getVersion(),
                order.getPlacedAt());
    }

    private OrderStatusHistoryJpaEntity toHistoryEntity(UUID orderId, OrderStatusTransition transition) {
        return new OrderStatusHistoryJpaEntity(
                orderId,
                transition.from() == null ? null : transition.from().name(),
                transition.to().name(),
                transition.actor().type().name(),
                transition.occurredAt());
    }

    /** Full reconstruction: header + items + history — 3 targeted queries, see class javadoc. */
    private Order toDomainFull(OrderJpaEntity entity) {
        List<LineSnapshot> lines = springDataOrderItemDao.findByOrderId(entity.getId()).stream()
                .map(i -> new LineSnapshot(ProductId.of(i.getProductId()), i.getProductName(),
                        new Money(i.getUnitPrice(), i.getCurrency()), Quantity.of(i.getQuantity())))
                .toList();
        List<OrderStatusTransition> history = springDataOrderStatusHistoryDao
                .findByOrderIdOrderByOccurredAtAsc(entity.getId()).stream()
                .map(h -> new OrderStatusTransition(
                        h.getFromStatus() == null ? null : OrderStatus.valueOf(h.getFromStatus()),
                        OrderStatus.valueOf(h.getToStatus()),
                        toActor(h.getActorType()),
                        h.getOccurredAt()))
                .toList();
        return toDomain(entity, lines, history);
    }

    private Order toDomain(OrderJpaEntity entity, List<LineSnapshot> lines, List<OrderStatusTransition> history) {
        String currency = entity.getCurrency();
        OrderTotals totals = new OrderTotals(
                new Money(entity.getItemsTotal(), currency),
                new Money(entity.getDiscountTotal(), currency),
                new Money(entity.getShippingTotal(), currency),
                new Money(entity.getGrandTotal(), currency));
        return Order.reconstitute(
                OrderId.of(entity.getId()),
                CustomerId.of(entity.getCustomerId()),
                OrderStatus.valueOf(entity.getStatus()),
                lines,
                totals,
                entity.getCouponCode(),
                ReservationId.of(entity.getReservationId()),
                history,
                entity.getVersion(),
                entity.getPlacedAt(),
                entity.getUpdatedAt());
    }

    /**
     * {@code order_status_history} has no {@code actor_id} column (V006's own comment: "who
     * specifically is out of this table's documented scope for v1") — reconstruction necessarily
     * loses the specific actor id, restoring only the {@link ActorType}, matching {@link Actor}'s
     * javadoc on why {@code actorId} is nullable.
     */
    private static Actor toActor(String actorType) {
        return new Actor(ActorType.valueOf(actorType), null);
    }
}

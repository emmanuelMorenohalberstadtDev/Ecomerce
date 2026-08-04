package com.ecommerce.order.infrastructure.persistence;

import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.auth.infrastructure.persistence.PostgresIntegrationTestBase;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.OrderRepository.OrderSummary;
import com.ecommerce.order.domain.exception.DuplicateOrderException;
import com.ecommerce.order.domain.exception.OrderConcurrentModificationException;
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.order.domain.port.out.PageResult;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link JpaOrderRepository} against a real PostgreSQL instance via
 * Testcontainers — mirrors
 * {@code checkout.infrastructure.persistence.JpaCheckoutSessionRepositoryIntegrationTest}'s pattern
 * exactly (the same {@link PostgresIntegrationTestBase} singleton container, the same
 * {@code @Transactional} per-test rollback isolation).
 *
 * <p>Not run in the default {@code mvn test} invocation — {@code @Tag("integration")} is excluded
 * by the surefire {@code excludedGroups=integration} configuration, matching every other repository
 * integration suite in this codebase. This environment has no Docker daemon, so this test does not
 * execute here; it only needs to compile and be logically correct (same disclosed limitation as
 * {@code JpaCheckoutSessionRepositoryIntegrationTest} and
 * {@code inventory.infrastructure.persistence.JpaStockItemRepositoryConcurrencyIntegrationTest}).
 *
 * <p>Covers what mocks cannot prove: the three-independent-DAO round trip ({@code orders} +
 * {@code order_items} + the initial {@code order_status_history} row all written by
 * {@link JpaOrderRepository#create} in one go — backend-lead's handoff-note risk, given no JPA
 * relationship maps the three tables together, see {@link OrderJpaEntity}'s class javadoc), that
 * {@link JpaOrderRepository#save} appends exactly one new history row per transition (never a
 * duplicate of a prior one), the ownership-scoped lookup, the {@code uq_orders_reservation}
 * idempotency backstop, and {@code @Version} optimistic-lock conflict detection.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class JpaOrderRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    // ── fixture helpers ────────────────────────────────────────────────────

    private CustomerId newSavedCustomer() {
        UserAccount user = UserAccount.create(
                new Email("order-" + UUID.randomUUID() + "@example.com"),
                new PasswordHash("$2a$12$someHashValueXXXXXXXXXXXXXX"));
        return CustomerId.of(userAccountRepository.save(user).getId().value());
    }

    private ProductId newSavedProduct() {
        CategoryId categoryId = categoryRepository.save(
                Category.create("Category-" + UUID.randomUUID(), null)).getId();
        Product product = Product.create(categoryId, new Sku("sku-" + UUID.randomUUID()),
                new Money(new BigDecimal("9.99"), "USD"), "Widget", null, List.of());
        return productRepository.save(product).getId();
    }

    /** {@code orders.reservation_id} carries a real FK to {@code stock_reservations} (V006). */
    private ReservationId newSavedReservation(ProductId productId) {
        StockReservation reservation = StockReservation.create(ReservationId.generate(), CheckoutSessionId.generate(),
                List.of(new ReservedLine(productId, Quantity.of(1))), Expiry.at(NOW.plusSeconds(900)), NOW);
        return stockReservationRepository.create(reservation).getId();
    }

    private Order newOrder(CustomerId customerId, ProductId productId, ReservationId reservationId) {
        LineSnapshot line = new LineSnapshot(productId, "Widget", new Money(new BigDecimal("10.00"), "USD"),
                Quantity.of(2));
        OrderTotals totals = new OrderTotals(new Money(new BigDecimal("20.00"), "USD"), Money.zero("USD"),
                new Money(new BigDecimal("5.00"), "USD"), new Money(new BigDecimal("25.00"), "USD"));
        return Order.place(OrderId.generate(), customerId, List.of(line), totals, null, reservationId,
                Actor.system(), NOW);
    }

    // ── create(): header + items + initial history row, in one go ──────────

    @Test
    void create_persistsHeaderItemsAndInitialHistoryRow_roundTrippingAllFields() {
        CustomerId customerId = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        Order order = newOrder(customerId, productId, reservationId);

        Order created = orderRepository.create(order);
        entityManager.flush();
        entityManager.clear();
        Optional<Order> found = orderRepository.findById(created.getId());

        assertThat(found).isPresent();
        Order loaded = found.get();
        assertThat(loaded.getCustomerId()).isEqualTo(customerId);
        assertThat(loaded.getReservationId()).isEqualTo(reservationId);
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getTotals().itemsTotal()).isEqualTo(new Money(new BigDecimal("20.00"), "USD"));
        assertThat(loaded.getTotals().grandTotal()).isEqualTo(new Money(new BigDecimal("25.00"), "USD"));

        assertThat(loaded.getLines()).hasSize(1);
        LineSnapshot line = loaded.getLines().get(0);
        assertThat(line.productId()).isEqualTo(productId);
        assertThat(line.productName()).isEqualTo("Widget");
        assertThat(line.quantity()).isEqualTo(Quantity.of(2));

        assertThat(loaded.getStatusHistory()).hasSize(1);
        assertThat(loaded.getStatusHistory().get(0).from()).isNull();
        assertThat(loaded.getStatusHistory().get(0).to()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void findById_returnsEmpty_whenOrderDoesNotExist() {
        Optional<Order> found = orderRepository.findById(OrderId.generate());

        assertThat(found).isEmpty();
    }

    @Test
    void create_throwsDuplicateOrderException_whenReservationAlreadyHasAnOrder() {
        CustomerId customerId = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        orderRepository.create(newOrder(customerId, productId, reservationId));
        entityManager.flush();

        Order second = newOrder(customerId, productId, reservationId);

        assertThatThrownBy(() -> orderRepository.create(second)).isInstanceOf(DuplicateOrderException.class);
    }

    // ── save(): exactly one NEW history row per transition, never a duplicate ──

    @Test
    void save_appendsExactlyOneNewHistoryRowPerTransition_neverDuplicatingPriorOnes() {
        CustomerId customerId = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        Order created = orderRepository.create(newOrder(customerId, productId, reservationId));
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(created.getId()).orElseThrow();
        reloaded.markPaid(Actor.admin(UUID.randomUUID()), NOW.plusSeconds(60));
        orderRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        Order afterFirstTransition = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(afterFirstTransition.getStatusHistory()).hasSize(2);

        afterFirstTransition.confirm(Actor.admin(UUID.randomUUID()), NOW.plusSeconds(120));
        orderRepository.save(afterFirstTransition);
        entityManager.flush();
        entityManager.clear();

        Order afterSecondTransition = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(afterSecondTransition.getStatusHistory()).hasSize(3);
        assertThat(afterSecondTransition.getStatusHistory())
                .extracting(t -> t.to())
                .containsExactly(OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.CONFIRMED);
        // The original line snapshot is untouched by any status-transition save — items are
        // write-once (rule 3), never re-inserted or duplicated by a subsequent save().
        assertThat(afterSecondTransition.getLines()).hasSize(1);
    }

    @Test
    void save_updatesStatusOnTheHeaderRow() {
        CustomerId customerId = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        Order created = orderRepository.create(newOrder(customerId, productId, reservationId));
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(created.getId()).orElseThrow();
        reloaded.markPaid(Actor.admin(UUID.randomUUID()), NOW.plusSeconds(60));
        orderRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ── ownership-scoped lookup ─────────────────────────────────────────────

    @Test
    void findByIdAndCustomerId_returnsEmpty_whenOrderBelongsToAnotherCustomer() {
        CustomerId owner = newSavedCustomer();
        CustomerId stranger = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        Order created = orderRepository.create(newOrder(owner, productId, reservationId));

        assertThat(orderRepository.findByIdAndCustomerId(created.getId(), stranger)).isEmpty();
        assertThat(orderRepository.findByIdAndCustomerId(created.getId(), owner)).isPresent();
    }

    // ── idempotency guard: findByReservationId ──────────────────────────────

    @Test
    void findByReservationId_returnsTheOrder_whenOneExistsForThatReservation() {
        CustomerId customerId = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        Order created = orderRepository.create(newOrder(customerId, productId, reservationId));

        Optional<Order> found = orderRepository.findByReservationId(reservationId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    void findByReservationId_returnsEmpty_whenNoOrderExistsForThatReservation() {
        assertThat(orderRepository.findByReservationId(ReservationId.generate())).isEmpty();
    }

    // ── pagination: findByCustomerId ────────────────────────────────────────

    @Test
    void findByCustomerId_returnsOnlyThatCustomersOrders_newestFirst() {
        CustomerId customerId = newSavedCustomer();
        CustomerId otherCustomer = newSavedCustomer();
        ProductId productId = newSavedProduct();
        orderRepository.create(newOrder(customerId, productId, newSavedReservation(productId)));
        orderRepository.create(newOrder(customerId, productId, newSavedReservation(productId)));
        orderRepository.create(newOrder(otherCustomer, productId, newSavedReservation(productId)));

        PageResult<OrderSummary> page = orderRepository.findByCustomerId(customerId, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).allMatch(s -> s.status().equals("PLACED"));
    }

    // ── optimistic locking (@Version) ───────────────────────────────────────

    @Test
    void save_throwsOrderConcurrentModificationException_onLostOptimisticLockRace() {
        CustomerId customerId = newSavedCustomer();
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        Order created = orderRepository.create(newOrder(customerId, productId, reservationId));
        entityManager.flush();
        entityManager.clear();

        Order loadedByFirstEditor = orderRepository.findById(created.getId()).orElseThrow();
        Order loadedBySecondEditor = orderRepository.findById(created.getId()).orElseThrow();

        loadedByFirstEditor.markPaid(Actor.admin(UUID.randomUUID()), NOW.plusSeconds(60));
        orderRepository.save(loadedByFirstEditor);
        entityManager.flush();
        entityManager.clear();

        loadedBySecondEditor.markPaid(Actor.admin(UUID.randomUUID()), NOW.plusSeconds(90));

        assertThatThrownBy(() -> orderRepository.save(loadedBySecondEditor))
                .isInstanceOf(OrderConcurrentModificationException.class);
    }
}

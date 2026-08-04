package com.ecommerce.payment.infrastructure.persistence;

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
import com.ecommerce.order.domain.model.Actor;
import com.ecommerce.order.domain.model.LineSnapshot;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderTotals;
import com.ecommerce.payment.domain.exception.DuplicatePaymentException;
import com.ecommerce.payment.domain.exception.PaymentConcurrentModificationException;
import com.ecommerce.payment.domain.model.DeclineReason;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentOutcome;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.model.RefundReason;
import com.ecommerce.payment.domain.port.out.PaymentRepository;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.PaymentId;
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
 * Integration tests for {@link JpaPaymentRepository} against a real PostgreSQL instance via
 * Testcontainers — mirrors
 * {@code order.infrastructure.persistence.JpaOrderRepositoryIntegrationTest}'s pattern exactly
 * (the same {@link PostgresIntegrationTestBase} singleton container, the same
 * {@code @Transactional} per-test rollback isolation).
 *
 * <p>Not run in the default {@code mvn test} invocation — {@code @Tag("integration")} is excluded
 * by the surefire {@code excludedGroups=integration} configuration, matching every other
 * repository integration suite in this codebase.
 *
 * <p>Covers what mocks cannot prove: the three-independent-DAO round trip ({@code payments} +
 * {@code payment_attempts} + {@code refunds}, all written by separate targeted DAOs per
 * {@link PaymentJpaEntity}'s class javadoc — no JPA relationship maps the three tables together),
 * the {@code uq_payments_order} one-payment-per-order idempotency backstop, the ownership-scoped
 * vs. system-scoped lookups, and {@code @Version} optimistic-lock conflict detection.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class JpaPaymentRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private PaymentRepository paymentRepository;

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

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    // ── fixture helpers ────────────────────────────────────────────────────

    private CustomerId newSavedCustomer() {
        UserAccount user = UserAccount.create(
                new Email("payment-" + UUID.randomUUID() + "@example.com"),
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

    private ReservationId newSavedReservation(ProductId productId) {
        StockReservation reservation = StockReservation.create(ReservationId.generate(), CheckoutSessionId.generate(),
                List.of(new ReservedLine(productId, Quantity.of(1))), Expiry.at(NOW.plusSeconds(900)), NOW);
        return stockReservationRepository.create(reservation).getId();
    }

    /** {@code payments.order_id} carries a real FK to {@code orders} (V007). */
    private OrderId newSavedOrder(CustomerId customerId, Money grandTotal) {
        ProductId productId = newSavedProduct();
        ReservationId reservationId = newSavedReservation(productId);
        LineSnapshot line = new LineSnapshot(productId, "Widget", grandTotal, Quantity.of(1));
        OrderTotals totals = new OrderTotals(grandTotal, Money.zero("USD"), Money.zero("USD"), grandTotal);
        Order order = Order.place(OrderId.generate(), customerId, List.of(line), totals, null, reservationId,
                Actor.system(), NOW);
        return orderRepository.create(order).getId();
    }

    private Payment newPayment(OrderId orderId, CustomerId customerId, Money amount) {
        return Payment.initiate(PaymentId.generate(), orderId, customerId, amount, NOW);
    }

    // ── create(): header only, no attempts/refunds yet ──────────────────────

    @Test
    void create_persistsHeader_roundTrippingAllFields() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        Payment payment = newPayment(orderId, customerId, amount);

        Payment created = paymentRepository.create(payment);
        entityManager.flush();
        entityManager.clear();
        Optional<Payment> found = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId);

        assertThat(found).isPresent();
        Payment loaded = found.get();
        assertThat(loaded.getOrderId()).isEqualTo(orderId);
        assertThat(loaded.getCustomerId()).isEqualTo(customerId);
        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(loaded.getAmount()).isEqualTo(amount);
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getAttempts()).isEmpty();
        assertThat(loaded.getRefunds()).isEmpty();
        assertThat(created.getId()).isEqualTo(loaded.getId());
    }

    @Test
    void create_throwsDuplicatePaymentException_whenOrderAlreadyHasAPayment() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        paymentRepository.create(newPayment(orderId, customerId, amount));
        entityManager.flush();

        Payment second = newPayment(orderId, customerId, amount);

        assertThatThrownBy(() -> paymentRepository.create(second)).isInstanceOf(DuplicatePaymentException.class);
    }

    // ── appendAttempt()/appendRefund(): child rows round-trip on reload ──────

    @Test
    void appendAttempt_persistsTheAttempt_visibleOnReload() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        Payment created = paymentRepository.create(newPayment(orderId, customerId, amount));
        entityManager.flush();
        entityManager.clear();

        Payment reloaded = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        reloaded.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1", NOW);
        paymentRepository.appendAttempt(reloaded.getId(), reloaded.getLatestAttempt());
        entityManager.flush();
        entityManager.clear();

        Payment afterAppend = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        assertThat(afterAppend.getAttempts()).hasSize(1);
        assertThat(afterAppend.getAttempts().get(0).idempotencyKey()).isEqualTo("key-1");
        assertThat(afterAppend.getAttempts().get(0).outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(afterAppend.getAttempts().get(0).declineReason()).isEqualTo(DeclineReason.CARD_DECLINED);
        assertThat(afterAppend.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void appendAttempt_appendsMultipleAttemptsInOrder_acrossRetries() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        Payment created = paymentRepository.create(newPayment(orderId, customerId, amount));
        entityManager.flush();
        entityManager.clear();

        Payment reloaded = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        reloaded.recordAttempt("key-1", PaymentOutcome.DECLINED, DeclineReason.CARD_DECLINED, "SIM-ref-1", NOW);
        paymentRepository.appendAttempt(reloaded.getId(), reloaded.getLatestAttempt());
        entityManager.flush();

        reloaded.recordAttempt("key-2", PaymentOutcome.APPROVED, null, "SIM-ref-2", NOW.plusSeconds(30));
        paymentRepository.appendAttempt(reloaded.getId(), reloaded.getLatestAttempt());
        entityManager.flush();
        entityManager.clear();

        Payment afterBoth = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        assertThat(afterBoth.getAttempts()).hasSize(2);
        assertThat(afterBoth.getAttempts()).extracting(a -> a.idempotencyKey())
                .containsExactly("key-1", "key-2");
    }

    @Test
    void appendRefund_persistsTheRefund_visibleOnReload() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        Payment created = paymentRepository.create(newPayment(orderId, customerId, amount));
        entityManager.flush();
        entityManager.clear();

        Payment reloaded = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        reloaded.recordAttempt("key-1", PaymentOutcome.APPROVED, null, "SIM-ref-1", NOW);
        paymentRepository.appendAttempt(reloaded.getId(), reloaded.getLatestAttempt());
        reloaded.markCaptured(NOW);
        paymentRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        Payment captured = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        captured.markRefunded(RefundReason.ORDER_CANCELLED, null, NOW.plusSeconds(60));
        paymentRepository.appendRefund(captured.getId(), captured.getLatestRefund());
        paymentRepository.save(captured);
        entityManager.flush();
        entityManager.clear();

        Payment afterRefund = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        assertThat(afterRefund.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(afterRefund.getRefunds()).hasSize(1);
        assertThat(afterRefund.getRefunds().get(0).reason()).isEqualTo(RefundReason.ORDER_CANCELLED);
        assertThat(afterRefund.getRefunds().get(0).amount()).isEqualTo(amount);
    }

    // ── save(): header status/version update ────────────────────────────────

    @Test
    void save_updatesStatusOnTheHeaderRow_afterMarkCaptured() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        Payment created = paymentRepository.create(newPayment(orderId, customerId, amount));
        entityManager.flush();
        entityManager.clear();

        Payment reloaded = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        reloaded.markCaptured(NOW);
        paymentRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        Payment afterSave = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        assertThat(afterSave.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    // ── ownership-scoped vs. system-scoped lookups ──────────────────────────

    @Test
    void findByOrderIdAndCustomerId_returnsEmpty_whenPaymentBelongsToAnotherCustomer() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId owner = newSavedCustomer();
        CustomerId stranger = newSavedCustomer();
        OrderId orderId = newSavedOrder(owner, amount);
        paymentRepository.create(newPayment(orderId, owner, amount));

        assertThat(paymentRepository.findByOrderIdAndCustomerId(orderId, stranger)).isEmpty();
        assertThat(paymentRepository.findByOrderIdAndCustomerId(orderId, owner)).isPresent();
    }

    @Test
    void findByOrderId_returnsThePayment_regardlessOfCustomer() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId owner = newSavedCustomer();
        OrderId orderId = newSavedOrder(owner, amount);
        Payment created = paymentRepository.create(newPayment(orderId, owner, amount));

        Optional<Payment> found = paymentRepository.findByOrderId(orderId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    void findByOrderId_returnsEmpty_whenNoPaymentExistsForThatOrder() {
        assertThat(paymentRepository.findByOrderId(OrderId.generate())).isEmpty();
    }

    // ── optimistic locking (@Version) ───────────────────────────────────────

    @Test
    void save_throwsPaymentConcurrentModificationException_onLostOptimisticLockRace() {
        Money amount = new Money(new BigDecimal("25.00"), "USD");
        CustomerId customerId = newSavedCustomer();
        OrderId orderId = newSavedOrder(customerId, amount);
        paymentRepository.create(newPayment(orderId, customerId, amount));
        entityManager.flush();
        entityManager.clear();

        Payment loadedByFirstEditor = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();
        Payment loadedBySecondEditor = paymentRepository.findByOrderIdAndCustomerId(orderId, customerId).orElseThrow();

        loadedByFirstEditor.markCaptured(NOW);
        paymentRepository.save(loadedByFirstEditor);
        entityManager.flush();
        entityManager.clear();

        loadedBySecondEditor.markCaptured(NOW.plusSeconds(30));

        assertThatThrownBy(() -> paymentRepository.save(loadedBySecondEditor))
                .isInstanceOf(PaymentConcurrentModificationException.class);
    }
}

package com.ecommerce.checkout.infrastructure.persistence;

import com.ecommerce.auth.domain.model.Email;
import com.ecommerce.auth.domain.model.PasswordHash;
import com.ecommerce.auth.domain.model.UserAccount;
import com.ecommerce.auth.domain.port.out.UserAccountRepository;
import com.ecommerce.auth.infrastructure.persistence.PostgresIntegrationTestBase;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.checkout.domain.exception.CheckoutSessionConcurrentModificationException;
import com.ecommerce.checkout.domain.exception.DuplicateIdempotencyKeyException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.model.SessionStatus;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.inventory.domain.model.Expiry;
import com.ecommerce.inventory.domain.model.ReservedLine;
import com.ecommerce.inventory.domain.model.StockReservation;
import com.ecommerce.inventory.domain.port.out.StockReservationRepository;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link JpaCheckoutSessionRepository} against a real PostgreSQL instance
 * via Testcontainers — mirrors {@code cart.infrastructure.persistence.JpaCartRepositoryIntegrationTest}'s
 * pattern exactly (the same {@link PostgresIntegrationTestBase} singleton container, the same
 * {@code @Transactional} per-test rollback isolation).
 *
 * <p>Not run in the default {@code mvn test} invocation — {@code @Tag("integration")} is excluded
 * by the surefire {@code excludedGroups=integration} configuration, matching every other
 * repository integration suite in this codebase. This environment has no Docker daemon, so this
 * test does not execute here; it only needs to compile and be logically correct (same disclosed
 * limitation as {@code inventory.infrastructure.persistence.JpaStockItemRepositoryConcurrencyIntegrationTest}).
 *
 * <p>Covers JPA + PostgreSQL behavior that cannot be proven with mocks: the totals-only round trip
 * (no per-line child table, per {@link CheckoutSessionJpaEntity}'s javadoc), the ownership-scoped
 * lookup, the {@code uq_checkout_sessions_idempotency} constraint now scoped per
 * {@code (customer_id, idempotency_key)} rather than globally, the {@code idx_checkout_sessions_customer_id}
 * -backed open-session query, the {@code idx_checkout_sessions_deadline_open}-backed expiry sweep
 * query, and {@code @Version} optimistic-lock conflict detection.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@Transactional
class JpaCheckoutSessionRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CartRepository cartRepository;

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
                new Email("checkout-" + UUID.randomUUID() + "@example.com"),
                new PasswordHash("$2a$12$someHashValueXXXXXXXXXXXXXX"));
        return CustomerId.of(userAccountRepository.save(user).getId().value());
    }

    private CartId newSavedCart(CustomerId customerId) {
        Cart cart = Cart.createCustomerCart(customerId);
        return cartRepository.save(cart).getId();
    }

    private ProductId newSavedProduct() {
        CategoryId categoryId = categoryRepository.save(
                Category.create("Category-" + UUID.randomUUID(), null)).getId();
        Product product = Product.create(categoryId, new Sku("sku-" + UUID.randomUUID()),
                new Money(new BigDecimal("9.99"), "USD"), "Widget", null, List.of());
        return productRepository.save(product).getId();
    }

    /** {@code stock_reservations.checkout_session_id} carries no FK (V004/V005 note) — any id is fine. */
    private ReservationId newSavedReservation(ProductId productId, Instant expiresAt) {
        StockReservation reservation = StockReservation.create(ReservationId.generate(), CheckoutSessionId.generate(),
                List.of(new ReservedLine(productId, Quantity.of(1))), Expiry.at(expiresAt), NOW);
        return stockReservationRepository.create(reservation).getId();
    }

    private static RecalculatedTotal totalOf(String grandTotal) {
        Money money = new Money(new BigDecimal(grandTotal), "USD");
        return new RecalculatedTotal(List.of(), money, Money.zero("USD"), Money.zero("USD"), money);
    }

    private CheckoutSession pendingSession(CustomerId customerId, CartId cartId, String idempotencyKey) {
        return CheckoutSession.start(CheckoutSessionId.generate(), customerId, cartId, totalOf("50.00"),
                new Money(new BigDecimal("50.00"), "USD"), idempotencyKey, NOW);
    }

    // ── round trip: PENDING_CONFIRMATION (no reservation) ─────────────────

    @Test
    void shouldPersistAndReloadPendingConfirmationSession_roundTrippingAllFields() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        CheckoutSession session = pendingSession(customerId, cartId, "key-" + UUID.randomUUID());

        CheckoutSession created = checkoutSessionRepository.create(session);
        Optional<CheckoutSession> found = checkoutSessionRepository.findById(created.getId());

        assertThat(found).isPresent();
        CheckoutSession loaded = found.get();
        assertThat(loaded.getCustomerId()).isEqualTo(customerId);
        assertThat(loaded.getCartId()).isEqualTo(cartId);
        assertThat(loaded.getStatus()).isEqualTo(SessionStatus.PENDING_CONFIRMATION);
        assertThat(loaded.getReservationId()).isNull();
        assertThat(loaded.getPaymentDeadline()).isNull();
        assertThat(loaded.getRecalculatedTotal().grandTotal().amount()).isEqualByComparingTo("50.00");
        assertThat(loaded.getExpectedTotal().amount()).isEqualByComparingTo("50.00");
        assertThat(loaded.getVersion()).isZero();
    }

    // ── round trip: AWAITING_PAYMENT (with a real reservation FK target) ──

    @Test
    void shouldPersistAndReloadAwaitingPaymentSession_withReservationId() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        ProductId productId = newSavedProduct();
        Instant deadline = NOW.plusSeconds(900);
        ReservationId reservationId = newSavedReservation(productId, deadline);
        CheckoutSession session = pendingSession(customerId, cartId, "key-" + UUID.randomUUID());
        session.moveToAwaitingPayment(reservationId, deadline);

        CheckoutSession created = checkoutSessionRepository.create(session);
        Optional<CheckoutSession> found = checkoutSessionRepository.findById(created.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(SessionStatus.AWAITING_PAYMENT);
        assertThat(found.get().getReservationId()).isEqualTo(reservationId);
        assertThat(found.get().getPaymentDeadline()).isEqualTo(deadline);
    }

    @Test
    void findById_returnsEmpty_whenSessionDoesNotExist() {
        Optional<CheckoutSession> found = checkoutSessionRepository.findById(CheckoutSessionId.generate());

        assertThat(found).isEmpty();
    }

    // ── ownership-scoped lookup ─────────────────────────────────────────────

    @Test
    void findByIdAndCustomerId_returnsEmpty_whenSessionBelongsToAnotherCustomer() {
        CustomerId owner = newSavedCustomer();
        CustomerId stranger = newSavedCustomer();
        CartId cartId = newSavedCart(owner);
        CheckoutSession created = checkoutSessionRepository.create(
                pendingSession(owner, cartId, "key-" + UUID.randomUUID()));

        assertThat(checkoutSessionRepository.findByIdAndCustomerId(created.getId(), stranger)).isEmpty();
        assertThat(checkoutSessionRepository.findByIdAndCustomerId(created.getId(), owner)).isPresent();
    }

    // ── idempotency-key scoping (uq_checkout_sessions_idempotency) ─────────

    @Test
    void findByCustomerIdAndIdempotencyKey_returnsSession_whenKeyMatches() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        String key = "key-" + UUID.randomUUID();
        CheckoutSession created = checkoutSessionRepository.create(pendingSession(customerId, cartId, key));

        Optional<CheckoutSession> found = checkoutSessionRepository.findByCustomerIdAndIdempotencyKey(customerId, key);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    void create_throwsDuplicateIdempotencyKeyException_whenSameCustomerReusesAnIdempotencyKey() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        String key = "key-" + UUID.randomUUID();
        checkoutSessionRepository.create(pendingSession(customerId, cartId, key));
        entityManager.flush();

        CheckoutSession secondAttempt = pendingSession(customerId, cartId, key);

        assertThatThrownBy(() -> checkoutSessionRepository.create(secondAttempt))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    @Test
    void create_allowsTheSameIdempotencyKey_forDifferentCustomers() {
        CustomerId customerA = newSavedCustomer();
        CustomerId customerB = newSavedCustomer();
        String sharedKey = "key-" + UUID.randomUUID();
        checkoutSessionRepository.create(pendingSession(customerA, newSavedCart(customerA), sharedKey));
        entityManager.flush();

        CheckoutSession createdForB = checkoutSessionRepository.create(
                pendingSession(customerB, newSavedCart(customerB), sharedKey));

        assertThat(createdForB.getIdempotencyKey()).isEqualTo(sharedKey);
    }

    // ── open-session guard query ────────────────────────────────────────────

    @Test
    void findOpenByCustomerId_returnsSession_whenPendingConfirmation() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        CheckoutSession created = checkoutSessionRepository.create(
                pendingSession(customerId, cartId, "key-" + UUID.randomUUID()));

        Optional<CheckoutSession> found = checkoutSessionRepository.findOpenByCustomerId(customerId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    void findOpenByCustomerId_returnsEmpty_whenCustomerHasNoSessionAtAll() {
        CustomerId customerId = newSavedCustomer();

        assertThat(checkoutSessionRepository.findOpenByCustomerId(customerId)).isEmpty();
    }

    // ── expiry sweep query ──────────────────────────────────────────────────

    @Test
    void findAwaitingPaymentExpiredAsOf_returnsOnlyAwaitingPaymentSessionsPastDeadline() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        ProductId productId = newSavedProduct();
        Instant pastDeadline = NOW.plusSeconds(1);
        Instant futureDeadline = NOW.plusSeconds(9_000);

        CheckoutSession expiredCandidate = pendingSession(customerId, cartId, "key-" + UUID.randomUUID());
        expiredCandidate.moveToAwaitingPayment(newSavedReservation(productId, pastDeadline), pastDeadline);
        checkoutSessionRepository.create(expiredCandidate);

        CheckoutSession notYetDue = pendingSession(customerId, newSavedCart(customerId), "key-" + UUID.randomUUID());
        notYetDue.moveToAwaitingPayment(newSavedReservation(productId, futureDeadline), futureDeadline);
        checkoutSessionRepository.create(notYetDue);

        checkoutSessionRepository.create(pendingSession(customerId, cartId, "key-" + UUID.randomUUID()));

        List<CheckoutSession> due = checkoutSessionRepository.findAwaitingPaymentExpiredAsOf(NOW.plusSeconds(60));

        assertThat(due).extracting(CheckoutSession::getId).containsExactly(expiredCandidate.getId());
    }

    // ── optimistic locking (@Version) ──────────────────────────────────────

    @Test
    void save_throwsCheckoutSessionConcurrentModificationException_onLostOptimisticLockRace() {
        CustomerId customerId = newSavedCustomer();
        CartId cartId = newSavedCart(customerId);
        CheckoutSession created = checkoutSessionRepository.create(
                pendingSession(customerId, cartId, "key-" + UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        CheckoutSession loadedByFirstEditor = checkoutSessionRepository.findById(created.getId()).orElseThrow();
        CheckoutSession loadedBySecondEditor = checkoutSessionRepository.findById(created.getId()).orElseThrow();

        loadedByFirstEditor.refreshRecalculatedTotal(totalOf("60.00"));
        checkoutSessionRepository.save(loadedByFirstEditor);
        entityManager.flush();
        entityManager.clear();

        loadedBySecondEditor.refreshRecalculatedTotal(totalOf("70.00"));

        assertThatThrownBy(() -> checkoutSessionRepository.save(loadedBySecondEditor))
                .isInstanceOf(CheckoutSessionConcurrentModificationException.class);
    }
}

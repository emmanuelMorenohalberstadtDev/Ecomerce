package com.ecommerce.cart.infrastructure.persistence;

import com.ecommerce.auth.infrastructure.persistence.PostgresIntegrationTestBase;
import com.ecommerce.cart.domain.exception.CartConcurrentModificationException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.ProductSnapshot;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Integration tests for {@link JpaCartRepository} against a real PostgreSQL instance via
 * Testcontainers.
 *
 * <p>Flyway applies V001+V002+V003 before the first test. {@code @Transactional} rolls back
 * after each test — no manual cleanup required.
 *
 * <p>Covers JPA + PostgreSQL behavior that cannot be proven with mocks: the {@code ProductSnapshot}
 * round trip staying immune to later catalog changes, the two partial-unique-index point lookups
 * ({@code uq_carts_customer_active}, {@code uq_carts_guest_token_active}), the {@code ck_carts_identity}
 * XOR constraint, the {@code uq_cart_items_cart_product} constraint, and {@code @Version}
 * optimistic-lock conflict detection.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@Transactional
class JpaCartRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    private ProductId newSavedProduct(String name, String price) {
        CategoryId categoryId = categoryRepository.save(Category.create("Category-" + UUID.randomUUID(), null)).getId();
        Product product = Product.create(categoryId, new Sku("sku-" + UUID.randomUUID()),
                new Money(new BigDecimal(price), "USD"), name, null, List.of());
        return productRepository.save(product).getId();
    }

    // ── save + findById round trip, snapshot immunity ─────────────────────

    @Test
    void shouldPersistAndReloadCart_preservingSnapshotFieldsVerbatim() {
        ProductId productId = newSavedProduct("Catalog Name At Save Time", "19.99");
        Cart cart = Cart.createCustomerCart(CustomerId.generate());
        cart.addItem(productId, new ProductSnapshot("Snapshot Name", new Money(new BigDecimal("19.99"), "USD")),
                Quantity.of(3), NOW);

        Cart saved = cartRepository.save(cart);
        Optional<Cart> found = cartRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLines()).hasSize(1);
        var line = found.get().getLines().get(0);
        assertThat(line.snapshot().name()).isEqualTo("Snapshot Name");
        assertThat(line.snapshot().unitPrice().amount()).isEqualByComparingTo("19.99");
        assertThat(line.snapshot().unitPrice().currency()).isEqualTo("USD");
        assertThat(line.quantity()).isEqualTo(Quantity.of(3));
    }

    @Test
    void shouldStaySnapshotImmune_whenUnderlyingCatalogProductLaterChanges() {
        ProductId productId = newSavedProduct("Original Catalog Name", "19.99");
        Cart cart = Cart.createCustomerCart(CustomerId.generate());
        cart.addItem(productId, new ProductSnapshot("Snapshot Name", new Money(new BigDecimal("19.99"), "USD")),
                Quantity.of(1), NOW);
        Cart saved = cartRepository.save(cart);

        // Change the underlying catalog product's price and name after the snapshot was taken.
        Product product = productRepository.findById(productId).orElseThrow();
        product.changePrice(new Money(new BigDecimal("999.00"), "USD"));
        product.updateDetails(product.getCategoryId(), "Renamed Later", "desc", List.of());
        productRepository.save(product);

        Optional<Cart> reloaded = cartRepository.findById(saved.getId());

        var line = reloaded.orElseThrow().getLines().get(0);
        assertThat(line.snapshot().name()).isEqualTo("Snapshot Name");
        assertThat(line.snapshot().unitPrice().amount()).isEqualByComparingTo("19.99");
    }

    @Test
    void findById_returnsEmpty_whenCartDoesNotExist() {
        Optional<Cart> found = cartRepository.findById(CartId.generate());

        assertThat(found).isEmpty();
    }

    // ── findActiveByCustomerId ──────────────────────────────────────────

    @Test
    void findActiveByCustomerId_returnsCart_whenActiveCartExists() {
        CustomerId customerId = CustomerId.generate();
        Cart saved = cartRepository.save(Cart.createCustomerCart(customerId));

        Optional<Cart> found = cartRepository.findActiveByCustomerId(customerId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findActiveByCustomerId_returnsEmpty_whenCustomerHasNoActiveCart() {
        Optional<Cart> found = cartRepository.findActiveByCustomerId(CustomerId.generate());

        assertThat(found).isEmpty();
    }

    // ── findActiveByGuestTokenHash ──────────────────────────────────────

    @Test
    void findActiveByGuestTokenHash_returnsCart_whenActiveCartExists() {
        Cart saved = cartRepository.save(Cart.createGuestCart("hash-" + UUID.randomUUID()));

        Optional<Cart> found = cartRepository.findActiveByGuestTokenHash(saved.getGuestTokenHash());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findActiveByGuestTokenHash_returnsEmpty_whenHashUnknown() {
        Optional<Cart> found = cartRepository.findActiveByGuestTokenHash("unknown-hash");

        assertThat(found).isEmpty();
    }

    @Test
    void findActiveByGuestTokenHash_excludesMergedCart() {
        String hash = "hash-" + UUID.randomUUID();
        Cart guestCart = Cart.createGuestCart(hash);
        Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
        customerCart.mergeFrom(guestCart); // guestCart -> MERGED, its hash cleared

        cartRepository.save(customerCart);
        cartRepository.save(guestCart);

        Optional<Cart> found = cartRepository.findActiveByGuestTokenHash(hash);

        assertThat(found).isEmpty();
    }

    // ── ck_carts_identity XOR constraint (DB backstop) ────────────────────

    @Test
    void insertingCartRow_withBothCustomerIdAndGuestTokenHash_violatesCkCartsIdentity() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO carts (id, customer_id, guest_token_hash, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 0, now(), now())",
                UUID.randomUUID(), UUID.randomUUID(), "some-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insertingCartRow_withNeitherCustomerIdNorGuestTokenHash_violatesCkCartsIdentity() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO carts (id, customer_id, guest_token_hash, status, version, created_at, updated_at) "
                        + "VALUES (?, NULL, NULL, 'ACTIVE', 0, now(), now())",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── uq_cart_items_cart_product constraint (DB backstop) ───────────────

    @Test
    void insertingDuplicateCartProductLine_violatesUqCartItemsCartProduct() {
        ProductId productId = newSavedProduct("Duplicate Target", "5.00");
        Cart saved = cartRepository.save(Cart.createGuestCart("hash-" + UUID.randomUUID()));
        UUID cartId = saved.getId().value();
        UUID prodId = productId.value();

        jdbcTemplate.update(
                "INSERT INTO cart_items (cart_id, product_id, product_name, unit_price, currency, quantity, "
                        + "unavailable, created_at) VALUES (?, ?, 'Line A', 5.00, 'USD', 1, false, now())",
                cartId, prodId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO cart_items (cart_id, product_id, product_name, unit_price, currency, quantity, "
                        + "unavailable, created_at) VALUES (?, ?, 'Line B', 5.00, 'USD', 1, false, now())",
                cartId, prodId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── optimistic locking (@Version) ─────────────────────────────────────

    @Test
    void save_throwsCartConcurrentModificationException_onLostOptimisticLockRace() {
        ProductId productA = newSavedProduct("Product A", "1.00");
        ProductId productB = newSavedProduct("Product B", "2.00");
        Cart cart = Cart.createCustomerCart(CustomerId.generate());
        Cart saved = cartRepository.save(cart);
        entityManager.flush();
        entityManager.clear();

        Cart loadedByFirstEditor = cartRepository.findById(saved.getId()).orElseThrow();
        Cart loadedBySecondEditor = cartRepository.findById(saved.getId()).orElseThrow();

        loadedByFirstEditor.addItem(productA,
                new ProductSnapshot("Product A", new Money(new BigDecimal("1.00"), "USD")), Quantity.of(1), NOW);
        cartRepository.save(loadedByFirstEditor);
        entityManager.flush();
        entityManager.clear();

        loadedBySecondEditor.addItem(productB,
                new ProductSnapshot("Product B", new Money(new BigDecimal("2.00"), "USD")), Quantity.of(1), NOW);

        assertThatThrownBy(() -> cartRepository.save(loadedBySecondEditor))
                .isInstanceOf(CartConcurrentModificationException.class);
    }
}

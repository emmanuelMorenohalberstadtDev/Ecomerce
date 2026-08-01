package com.ecommerce.inventory.infrastructure.persistence;

import com.ecommerce.auth.infrastructure.persistence.PostgresIntegrationTestBase;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.inventory.domain.model.StockItem;
import com.ecommerce.inventory.domain.port.out.StockItemRepository;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving {@link JpaStockItemRepository#decrementIfSufficientStock} — the atomic
 * conditional {@code UPDATE stock_items SET quantity_available = quantity_available - :n WHERE
 * product_id = :p AND quantity_available >= :n} behind checkout-time reservation (rule 6,
 * database-design.md §3.4) — actually prevents oversell under concurrent access, against a real
 * PostgreSQL instance via Testcontainers.
 *
 * <p>Not run in the default {@code mvn test} invocation — {@code @Tag("integration")} is excluded
 * by the surefire {@code excludedGroups=integration} configuration (matching the convention
 * {@code JpaCartRepositoryIntegrationTest} and the other context integration suites already use).
 * This environment has no Docker daemon, so this test does not execute here; it only needs to
 * compile and be logically correct.
 *
 * <p><strong>Deliberately NOT {@code @Transactional} at the class level</strong>, unlike the other
 * repository integration tests in this codebase ({@code JpaCartRepositoryIntegrationTest}): this
 * test needs two genuinely separate, concurrently-committing database transactions racing on the
 * same row. A class-level {@code @Transactional} binds the JUnit test method's connection/
 * transaction to the calling thread only (Spring's transaction synchronization is thread-local) —
 * the two worker threads spawned below open their own connections and their own transactions
 * regardless, so wrapping the outer test in one would only mean the setup data (product + initial
 * stock row) is never actually committed for those worker connections to see, and would silently
 * roll back everything at the end rather than proving anything about the real race. Setup data is
 * therefore committed for real, and cleaned up manually in {@link #cleanUp()}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class JpaStockItemRepositoryConcurrencyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ProductId productId;

    @AfterEach
    void cleanUp() {
        if (productId != null) {
            jdbcTemplate.update("DELETE FROM stock_items WHERE product_id = ?", productId.value());
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId.value());
        }
    }

    /**
     * Two threads race to reserve the last unit of stock via
     * {@link StockItemRepository#decrementIfSufficientStock} concurrently, each on its own
     * connection/transaction. Exactly one must succeed (the atomic decrement) and the other must
     * observe a shortage (zero rows affected) — never both succeeding (oversell, the exact bug
     * this schema-level guard exists to prevent) and never both failing.
     */
    @Test
    void shouldAllowExactlyOneConcurrentReservation_whenOnlyOneUnitAvailable() throws Exception {
        CategoryId categoryId = categoryRepository.save(
                Category.create("Category-" + UUID.randomUUID(), null)).getId();
        Product product = Product.create(categoryId, new Sku("sku-" + UUID.randomUUID()),
                new Money(new BigDecimal("9.99"), "USD"), "Contested Product", null, List.of());
        productId = productRepository.save(product).getId();
        stockItemRepository.save(StockItem.reconstitute(productId, 1, 0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Boolean> firstAttempt = executor.submit(() -> raceToDecrement(startLatch));
            Future<Boolean> secondAttempt = executor.submit(() -> raceToDecrement(startLatch));

            startLatch.countDown();

            boolean firstSucceeded = firstAttempt.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = secondAttempt.get(10, TimeUnit.SECONDS);

            assertThat(firstSucceeded ^ secondSucceeded)
                    .as("exactly one of the two concurrent reservations for the last unit must succeed")
                    .isTrue();

            StockItem reloaded = stockItemRepository.findById(productId).orElseThrow();
            assertThat(reloaded.getAvailable().value())
                    .as("the single available unit must be fully consumed, never negative, never still 1")
                    .isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean raceToDecrement(CountDownLatch startLatch) throws InterruptedException {
        startLatch.await();
        return stockItemRepository.decrementIfSufficientStock(productId, 1);
    }
}

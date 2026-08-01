package com.ecommerce.cart.domain.model;

import com.ecommerce.cart.domain.exception.CartLineNotFoundException;
import com.ecommerce.cart.domain.exception.CartNotActiveException;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Cart} aggregate root.
 *
 * <p>No Spring context — pure domain logic via factories and domain methods.
 */
@Tag("unit")
class CartTest {

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-31T13:00:00Z");

    private static ProductSnapshot snapshot(String name, String price) {
        return new ProductSnapshot(name, new Money(new BigDecimal(price), "USD"));
    }

    // ── factories ──────────────────────────────────────────────────────────

    @Test
    void createGuestCart_startsActiveWithGuestTokenHashOnly() {
        Cart cart = Cart.createGuestCart("hash-abc");

        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cart.getGuestTokenHash()).isEqualTo("hash-abc");
        assertThat(cart.getCustomerId()).isNull();
        assertThat(cart.getLines()).isEmpty();
    }

    @Test
    void createGuestCart_throwsIllegalArgumentException_whenTokenHashBlank() {
        assertThatThrownBy(() -> Cart.createGuestCart("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createCustomerCart_startsActiveWithCustomerIdOnly() {
        CustomerId customerId = CustomerId.generate();

        Cart cart = Cart.createCustomerCart(customerId);

        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cart.getCustomerId()).isEqualTo(customerId);
        assertThat(cart.getGuestTokenHash()).isNull();
    }

    @Test
    void createCustomerCart_throwsIllegalArgumentException_whenCustomerIdNull() {
        assertThatThrownBy(() -> Cart.createCustomerCart(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenBothCustomerIdAndGuestTokenHashPresent() {
        assertThatThrownBy(() -> Cart.reconstitute(
                CartId.generate(), CustomerId.generate(), "hash", CartStatus.ACTIVE, 0L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenNeitherCustomerIdNorGuestTokenHashPresent() {
        assertThatThrownBy(() -> Cart.reconstitute(
                CartId.generate(), null, null, CartStatus.ACTIVE, 0L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_throwsIllegalArgumentException_whenVersionIsNegative() {
        assertThatThrownBy(() -> Cart.reconstitute(
                CartId.generate(), CustomerId.generate(), null, CartStatus.ACTIVE, -1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_restoresExactState() {
        CartId id = CartId.generate();
        CustomerId customerId = CustomerId.generate();
        ProductId productId = ProductId.generate();
        CartLine line = new CartLine(productId, snapshot("Widget", "9.99"), Quantity.of(2), false, NOW);

        Cart cart = Cart.reconstitute(id, customerId, null, CartStatus.MERGED, 5L, List.of(line));

        assertThat(cart.getId()).isEqualTo(id);
        assertThat(cart.getCustomerId()).isEqualTo(customerId);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.MERGED);
        assertThat(cart.getVersion()).isEqualTo(5L);
        assertThat(cart.getLines()).containsExactly(line);
    }

    // ── addItem ────────────────────────────────────────────────────────────

    @Test
    void addItem_addsNewLine_whenProductNotAlreadyInCart() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();

        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(2), NOW);

        assertThat(cart.getLines()).hasSize(1);
        CartLine line = cart.getLines().get(0);
        assertThat(line.productId()).isEqualTo(productId);
        assertThat(line.quantity()).isEqualTo(Quantity.of(2));
        assertThat(line.addedAt()).isEqualTo(NOW);
        assertThat(line.unavailable()).isFalse();
    }

    @Test
    void addItem_sumsQuantities_whenProductAlreadyInCart() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(2), NOW);

        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(3), LATER);

        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getLines().get(0).quantity()).isEqualTo(Quantity.of(5));
    }

    @Test
    void addItem_preservesOriginalAddedAt_whenUpsertingExistingLine() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(2), NOW);

        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), LATER);

        assertThat(cart.getLines().get(0).addedAt()).isEqualTo(NOW);
    }

    @Test
    void addItem_refreshesSnapshot_whenUpsertingExistingLine() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);

        cart.addItem(productId, snapshot("Widget New Name", "12.00"), Quantity.of(1), LATER);

        assertThat(cart.getLines().get(0).snapshot()).isEqualTo(snapshot("Widget New Name", "12.00"));
    }

    @Test
    void addItem_capsAtMaxQuantityPerLine_whenNewLineExceedsCap() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();

        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(150), NOW);

        assertThat(cart.getLines().get(0).quantity().value()).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
    }

    @Test
    void addItem_capsAtMaxQuantityPerLine_whenSummedQuantityExceedsCap() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(90), NOW);

        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(50), LATER);

        assertThat(cart.getLines().get(0).quantity().value()).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
    }

    @Test
    void addItem_throwsCartNotActiveException_whenCartIsNotActive() {
        Cart cart = Cart.createGuestCart("hash");
        cart.markMerged();

        assertThatThrownBy(() -> cart.addItem(
                ProductId.generate(), snapshot("Widget", "9.99"), Quantity.of(1), NOW))
                .isInstanceOf(CartNotActiveException.class);
    }

    // ── updateLineQuantity ────────────────────────────────────────────────

    @Test
    void updateLineQuantity_replacesQuantity_preservingSnapshotAndAddedAt() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(2), NOW);

        cart.updateLineQuantity(productId, Quantity.of(7));

        CartLine line = cart.getLines().get(0);
        assertThat(line.quantity()).isEqualTo(Quantity.of(7));
        assertThat(line.snapshot()).isEqualTo(snapshot("Widget", "9.99"));
        assertThat(line.addedAt()).isEqualTo(NOW);
    }

    @Test
    void updateLineQuantity_capsAtMaxQuantityPerLine() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);

        cart.updateLineQuantity(productId, Quantity.of(500));

        assertThat(cart.getLines().get(0).quantity().value()).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
    }

    @Test
    void updateLineQuantity_throwsCartLineNotFoundException_whenProductNotInCart() {
        Cart cart = Cart.createGuestCart("hash");

        assertThatThrownBy(() -> cart.updateLineQuantity(ProductId.generate(), Quantity.of(1)))
                .isInstanceOf(CartLineNotFoundException.class);
    }

    @Test
    void updateLineQuantity_throwsCartNotActiveException_whenCartIsNotActive() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);
        cart.markMerged();

        assertThatThrownBy(() -> cart.updateLineQuantity(productId, Quantity.of(2)))
                .isInstanceOf(CartNotActiveException.class);
    }

    // ── removeLine ────────────────────────────────────────────────────────

    @Test
    void removeLine_removesExistingLine() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);

        cart.removeLine(productId);

        assertThat(cart.getLines()).isEmpty();
    }

    @Test
    void removeLine_throwsCartLineNotFoundException_whenProductNotInCart() {
        Cart cart = Cart.createGuestCart("hash");

        assertThatThrownBy(() -> cart.removeLine(ProductId.generate()))
                .isInstanceOf(CartLineNotFoundException.class);
    }

    @Test
    void removeLine_throwsCartNotActiveException_whenCartIsNotActive() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);
        cart.markMerged();

        assertThatThrownBy(() -> cart.removeLine(productId))
                .isInstanceOf(CartNotActiveException.class);
    }

    // ── markLineUnavailable ───────────────────────────────────────────────

    @Test
    void markLineUnavailable_flagsExistingLine() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);

        cart.markLineUnavailable(productId);

        assertThat(cart.getLines().get(0).unavailable()).isTrue();
    }

    @Test
    void markLineUnavailable_isNoOp_whenProductNotInCart() {
        Cart cart = Cart.createGuestCart("hash");

        cart.markLineUnavailable(ProductId.generate());

        assertThat(cart.getLines()).isEmpty();
    }

    @Test
    void markLineUnavailable_doesNotRequireActiveCart() {
        Cart cart = Cart.createGuestCart("hash");
        ProductId productId = ProductId.generate();
        cart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(1), NOW);
        cart.markMerged();

        cart.markLineUnavailable(productId);

        assertThat(cart.getLines().get(0).unavailable()).isTrue();
    }

    // ── mergeFrom ─────────────────────────────────────────────────────────

    @Nested
    class MergeFrom {

        @Test
        void sumsQuantities_whenSameProductInBothCarts() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            ProductId productId = ProductId.generate();
            customerCart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(2), NOW);
            guestCart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(3), NOW);

            customerCart.mergeFrom(guestCart);

            assertThat(customerCart.getLines()).hasSize(1);
            assertThat(customerCart.getLines().get(0).quantity()).isEqualTo(Quantity.of(5));
        }

        @Test
        void capsSummedQuantityAtMaxQuantityPerLine() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            ProductId productId = ProductId.generate();
            customerCart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(80), NOW);
            guestCart.addItem(productId, snapshot("Widget", "9.99"), Quantity.of(80), NOW);

            customerCart.mergeFrom(guestCart);

            assertThat(customerCart.getLines().get(0).quantity().value()).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
        }

        @Test
        void copiesGuestOnlyLinesOver() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            ProductId guestOnlyProduct = ProductId.generate();
            guestCart.addItem(guestOnlyProduct, snapshot("Gadget", "5.00"), Quantity.of(1), NOW);

            customerCart.mergeFrom(guestCart);

            assertThat(customerCart.getLines()).hasSize(1);
            assertThat(customerCart.getLines().get(0).productId()).isEqualTo(guestOnlyProduct);
        }

        @Test
        void doesNotTouchCustomerOnlyLines() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            ProductId customerOnlyProduct = ProductId.generate();
            customerCart.addItem(customerOnlyProduct, snapshot("Widget", "9.99"), Quantity.of(1), NOW);

            customerCart.mergeFrom(guestCart);

            assertThat(customerCart.getLines()).hasSize(1);
            assertThat(customerCart.getLines().get(0).productId()).isEqualTo(customerOnlyProduct);
        }

        @Test
        void transitionsGuestCartToMergedAndClearsItsTokenHash() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");

            customerCart.mergeFrom(guestCart);

            assertThat(guestCart.getStatus()).isEqualTo(CartStatus.MERGED);
            assertThat(guestCart.getGuestTokenHash()).isNull();
        }

        @Test
        void freshestSnapshotWins_whenConflictingSnapshotsForSameProduct() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            ProductId productId = ProductId.generate();
            customerCart.addItem(productId, snapshot("Stale Name", "9.99"), Quantity.of(1), NOW);
            guestCart.addItem(productId, snapshot("Fresh Name", "12.00"), Quantity.of(1), LATER);

            customerCart.mergeFrom(guestCart);

            CartLine merged = customerCart.getLines().get(0);
            assertThat(merged.snapshot()).isEqualTo(snapshot("Fresh Name", "12.00"));
            assertThat(merged.addedAt()).isEqualTo(LATER);
        }

        @Test
        void staleSnapshotIsDiscarded_whenGuestLineIsOlder() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            ProductId productId = ProductId.generate();
            customerCart.addItem(productId, snapshot("Fresh Name", "12.00"), Quantity.of(1), LATER);
            guestCart.addItem(productId, snapshot("Stale Name", "9.99"), Quantity.of(1), NOW);

            customerCart.mergeFrom(guestCart);

            CartLine merged = customerCart.getLines().get(0);
            assertThat(merged.snapshot()).isEqualTo(snapshot("Fresh Name", "12.00"));
            assertThat(merged.addedAt()).isEqualTo(LATER);
        }

        @Test
        void throwsCartNotActiveException_whenThisCartIsNotActive() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            customerCart.markMerged();

            assertThatThrownBy(() -> customerCart.mergeFrom(guestCart))
                    .isInstanceOf(CartNotActiveException.class);
        }

        @Test
        void throwsCartNotActiveException_whenGuestCartIsNotActive() {
            Cart customerCart = Cart.createCustomerCart(CustomerId.generate());
            Cart guestCart = Cart.createGuestCart("hash");
            guestCart.markMerged();

            assertThatThrownBy(() -> customerCart.mergeFrom(guestCart))
                    .isInstanceOf(CartNotActiveException.class);
        }
    }

    // ── claimForCustomer ──────────────────────────────────────────────────

    @Test
    void claimForCustomer_transfersOwnershipAndClearsGuestTokenHash() {
        Cart guestCart = Cart.createGuestCart("hash");
        CustomerId customerId = CustomerId.generate();

        guestCart.claimForCustomer(customerId);

        assertThat(guestCart.getCustomerId()).isEqualTo(customerId);
        assertThat(guestCart.getGuestTokenHash()).isNull();
    }

    @Test
    void claimForCustomer_throwsIllegalStateException_whenCartAlreadyBelongsToACustomer() {
        Cart cart = Cart.createCustomerCart(CustomerId.generate());

        assertThatThrownBy(() -> cart.claimForCustomer(CustomerId.generate()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claimForCustomer_throwsCartNotActiveException_whenCartIsNotActive() {
        Cart guestCart = Cart.createGuestCart("hash");
        guestCart.markMerged();

        assertThatThrownBy(() -> guestCart.claimForCustomer(CustomerId.generate()))
                .isInstanceOf(CartNotActiveException.class);
    }

    // ── getLines defensive copy ───────────────────────────────────────────

    @Test
    void getLines_returnsUnmodifiableList() {
        Cart cart = Cart.createGuestCart("hash");
        cart.addItem(ProductId.generate(), snapshot("Widget", "9.99"), Quantity.of(1), NOW);

        assertThatThrownBy(() -> cart.getLines().add(
                new CartLine(ProductId.generate(), snapshot("Other", "1.00"), Quantity.of(1), false, NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── equals/hashCode ────────────────────────────────────────────────────

    @Test
    void equals_isBasedOnId() {
        CartId id = CartId.generate();
        Cart a = Cart.reconstitute(id, CustomerId.generate(), null, CartStatus.ACTIVE, 0L, List.of());
        Cart b = Cart.reconstitute(id, CustomerId.generate(), null, CartStatus.MERGED, 3L, List.of());

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void equals_returnsFalse_whenIdsDiffer() {
        Cart a = Cart.createGuestCart("hash-a");
        Cart b = Cart.createGuestCart("hash-b");

        assertThat(a).isNotEqualTo(b);
    }
}

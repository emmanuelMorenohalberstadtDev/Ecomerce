package com.ecommerce.cart.infrastructure.adapter;

import com.ecommerce.cart.application.port.CartReadPort.CartSnapshot;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.ProductSnapshot;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CartReadAdapter} — the thin projection from cart's full {@link Cart}
 * aggregate to the small {@link CartSnapshot} shape other bounded contexts (checkout) are allowed
 * to see. Focused adapter test per the checkout-context test-implementation brief; cart's other
 * cross-context adapter ({@code ProductCatalogAdapter}) has no dedicated test of its own in this
 * codebase, so this class intentionally stays minimal — DTO translation only, no exception
 * translation happens here.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CartReadAdapterTest {

    @Mock
    private CartRepository cartRepository;

    private CartReadAdapter adapter;

    private final CustomerId customerId = CustomerId.generate();

    @BeforeEach
    void setUp() {
        adapter = new CartReadAdapter(cartRepository);
    }

    @Test
    void findActiveCartByCustomer_returnsProjectedSnapshot_whenActiveCartExists() {
        Cart cart = Cart.createCustomerCart(customerId);
        ProductId productId = ProductId.generate();
        cart.addItem(productId, new ProductSnapshot("Widget", new Money(new BigDecimal("9.99"), "USD")),
                Quantity.of(3), Instant.parse("2026-07-31T12:00:00Z"));
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(cart));

        Optional<CartSnapshot> result = adapter.findActiveCartByCustomer(customerId);

        assertThat(result).isPresent();
        assertThat(result.get().cartId()).isEqualTo(cart.getId());
        assertThat(result.get().lines()).hasSize(1);
        assertThat(result.get().lines().get(0).productId()).isEqualTo(productId);
        assertThat(result.get().lines().get(0).quantity()).isEqualTo(Quantity.of(3));
    }

    @Test
    void findActiveCartByCustomer_returnsEmpty_whenNoActiveCart() {
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.empty());

        Optional<CartSnapshot> result = adapter.findActiveCartByCustomer(customerId);

        assertThat(result).isEmpty();
    }
}

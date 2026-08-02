package com.ecommerce.checkout.infrastructure.adapter;

import com.ecommerce.cart.application.port.CartReadPort;
import com.ecommerce.checkout.domain.port.out.CartLinesPort.CartSnapshot;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CartLinesAdapter} — the sole translation of cart's
 * {@link CartReadPort.CartSnapshot} into checkout's own {@link CartSnapshot}. Focused adapter test
 * (per the checkout test-implementation brief) covering DTO translation only; no exception
 * translation happens in this adapter.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CartLinesAdapterTest {

    @Mock
    private CartReadPort cartReadPort;

    private CartLinesAdapter adapter;

    private final CustomerId customerId = CustomerId.generate();

    @BeforeEach
    void setUp() {
        adapter = new CartLinesAdapter(cartReadPort);
    }

    @Test
    void findActiveCartLines_returnsCheckoutShapedSnapshot_whenCartReadPortHasAnActiveCart() {
        CartId cartId = CartId.generate();
        ProductId productId = ProductId.generate();
        CartReadPort.CartSnapshot cartSnapshot = new CartReadPort.CartSnapshot(cartId,
                List.of(new CartReadPort.CartLineView(productId, Quantity.of(3))));
        when(cartReadPort.findActiveCartByCustomer(customerId)).thenReturn(Optional.of(cartSnapshot));

        Optional<CartSnapshot> result = adapter.findActiveCartLines(customerId);

        assertThat(result).isPresent();
        assertThat(result.get().cartId()).isEqualTo(cartId);
        assertThat(result.get().lines()).hasSize(1);
        assertThat(result.get().lines().get(0).productId()).isEqualTo(productId);
        assertThat(result.get().lines().get(0).quantity()).isEqualTo(Quantity.of(3));
    }

    @Test
    void findActiveCartLines_returnsEmpty_whenCustomerHasNoActiveCart() {
        when(cartReadPort.findActiveCartByCustomer(customerId)).thenReturn(Optional.empty());

        Optional<CartSnapshot> result = adapter.findActiveCartLines(customerId);

        assertThat(result).isEmpty();
    }
}

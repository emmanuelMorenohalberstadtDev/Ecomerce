package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.application.usecase.UpdateLineQuantityUseCase.UpdateLineQuantityCommand;
import com.ecommerce.cart.domain.exception.CartLineNotFoundException;
import com.ecommerce.cart.domain.exception.CartNotFoundException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.ProductSnapshot;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.security.GuestTokenHasher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UpdateLineQuantityUseCase}. Mocks {@link CartRepository} only —
 * cart-resolution ("own cart by identity") and quantity-replacement are exercised through the
 * real domain {@link Cart}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UpdateLineQuantityUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    private UpdateLineQuantityUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new UpdateLineQuantityUseCase(cartRepository);
    }

    private Cart cartWithLine(CustomerId owner) {
        Cart cart = Cart.createCustomerCart(owner);
        cart.addItem(productId, new ProductSnapshot("Widget", new Money(new BigDecimal("9.99"), "USD")),
                Quantity.of(1), Instant.parse("2026-07-31T12:00:00Z"));
        return cart;
    }

    @Test
    void shouldUpdateQuantity_whenCustomerOwnsCart() {
        Cart cart = cartWithLine(customerId);
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = useCase.execute(new UpdateLineQuantityCommand(customerId, null, productId, Quantity.of(9)));

        assertThat(result.getLines().get(0).quantity()).isEqualTo(Quantity.of(9));
    }

    @Test
    void shouldUpdateQuantity_whenGuestOwnsCart() {
        String rawToken = "raw-guest-token";
        String hash = GuestTokenHasher.sha256Hex(rawToken);
        Cart cart = Cart.createGuestCart(hash);
        cart.addItem(productId, new ProductSnapshot("Widget", new Money(new BigDecimal("9.99"), "USD")),
                Quantity.of(1), Instant.parse("2026-07-31T12:00:00Z"));
        when(cartRepository.findActiveByGuestTokenHash(hash)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = useCase.execute(new UpdateLineQuantityCommand(null, rawToken, productId, Quantity.of(4)));

        assertThat(result.getLines().get(0).quantity()).isEqualTo(Quantity.of(4));
    }

    @Test
    void shouldThrowCartNotFoundException_whenCustomerHasNoActiveCart() {
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new UpdateLineQuantityCommand(customerId, null, productId, Quantity.of(1))))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void shouldThrowCartNotFoundException_whenNoIdentityResolves() {
        assertThatThrownBy(() -> useCase.execute(new UpdateLineQuantityCommand(null, null, productId, Quantity.of(1))))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void shouldThrowCartLineNotFoundException_whenProductNotInCart() {
        Cart cart = Cart.createCustomerCart(customerId);
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> useCase.execute(
                new UpdateLineQuantityCommand(customerId, null, ProductId.generate(), Quantity.of(1))))
                .isInstanceOf(CartLineNotFoundException.class);
    }
}

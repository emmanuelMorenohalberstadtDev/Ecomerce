package com.ecommerce.cart.application.usecase;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MergeCartsUseCase} — the two cases (direct claim when the customer has
 * no existing cart; merge-in when they do) plus the no-op paths.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MergeCartsUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    private MergeCartsUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final String rawToken = "raw-guest-token";
    private final String tokenHash = GuestTokenHasher.sha256Hex("raw-guest-token");

    @BeforeEach
    void setUp() {
        useCase = new MergeCartsUseCase(cartRepository);
    }

    @Test
    void shouldDoNothing_whenNoGuestTokenProvided() {
        useCase.execute(customerId, null);

        verify(cartRepository, never()).findActiveByGuestTokenHash(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldDoNothing_whenGuestTokenBlank() {
        useCase.execute(customerId, "   ");

        verify(cartRepository, never()).findActiveByGuestTokenHash(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldDoNothing_whenGuestCartNotFound() {
        when(cartRepository.findActiveByGuestTokenHash(tokenHash)).thenReturn(Optional.empty());

        useCase.execute(customerId, rawToken);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldClaimGuestCartDirectly_whenCustomerHasNoExistingActiveCart() {
        Cart guestCart = Cart.createGuestCart(tokenHash);
        when(cartRepository.findActiveByGuestTokenHash(tokenHash)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(customerId, rawToken);

        assertThat(guestCart.getCustomerId()).isEqualTo(customerId);
        assertThat(guestCart.getGuestTokenHash()).isNull();
        verify(cartRepository, times(1)).save(guestCart);
    }

    @Test
    void shouldMergeGuestCartIntoExistingCustomerCart_whenOneExists() {
        Cart guestCart = Cart.createGuestCart(tokenHash);
        ProductId productId = ProductId.generate();
        guestCart.addItem(productId, new ProductSnapshot("Widget", new Money(new BigDecimal("9.99"), "USD")),
                Quantity.of(2), Instant.parse("2026-07-31T12:00:00Z"));

        Cart customerCart = Cart.createCustomerCart(customerId);

        when(cartRepository.findActiveByGuestTokenHash(tokenHash)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(customerCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(customerId, rawToken);

        assertThat(customerCart.getLines()).hasSize(1);
        assertThat(customerCart.getLines().get(0).productId()).isEqualTo(productId);
        assertThat(guestCart.getStatus().name()).isEqualTo("MERGED");
        verify(cartRepository).save(customerCart);
        verify(cartRepository).save(guestCart);
    }
}

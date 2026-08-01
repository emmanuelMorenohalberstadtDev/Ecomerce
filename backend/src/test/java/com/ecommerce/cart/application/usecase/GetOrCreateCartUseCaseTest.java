package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer;
import com.ecommerce.cart.domain.port.out.GuestCartTokenIssuer.IssuedGuestToken;
import com.ecommerce.cart.domain.security.GuestTokenHasher;
import com.ecommerce.shared.id.CustomerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetOrCreateCartUseCase} — both identity paths (authenticated customer,
 * anonymous guest) and the guest sub-cases (existing token resolves / stale or absent token
 * mints a new one).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GetOrCreateCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private GuestCartTokenIssuer guestCartTokenIssuer;

    private GetOrCreateCartUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();

    @BeforeEach
    void setUp() {
        useCase = new GetOrCreateCartUseCase(cartRepository, guestCartTokenIssuer);
    }

    // ── customer path ─────────────────────────────────────────────────────

    @Test
    void shouldReturnExistingCart_whenCustomerHasActiveCart() {
        Cart existing = Cart.createCustomerCart(customerId);
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(existing));

        var result = useCase.execute(customerId, null);

        assertThat(result.cart()).isEqualTo(existing);
        assertThat(result.created()).isFalse();
        assertThat(result.newRawGuestToken()).isNull();
    }

    @Test
    void shouldCreateNewCart_whenCustomerHasNoActiveCart() {
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(customerId, null);

        assertThat(result.created()).isTrue();
        assertThat(result.cart().getCustomerId()).isEqualTo(customerId);
        assertThat(result.newRawGuestToken()).isNull();
    }

    @Test
    void shouldNeverInvokeGuestTokenIssuer_whenCustomerIdProvided() {
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(customerId, null);

        verify(guestCartTokenIssuer, never()).issue();
    }

    // ── guest path ────────────────────────────────────────────────────────

    @Test
    void shouldReturnExistingGuestCart_whenRawTokenHashResolves() {
        String rawToken = "raw-guest-token";
        String hash = GuestTokenHasher.sha256Hex(rawToken);
        Cart existing = Cart.createGuestCart(hash);
        when(cartRepository.findActiveByGuestTokenHash(hash)).thenReturn(Optional.of(existing));

        var result = useCase.execute(null, rawToken);

        assertThat(result.cart()).isEqualTo(existing);
        assertThat(result.created()).isFalse();
        assertThat(result.newRawGuestToken()).isNull();
    }

    @Test
    void shouldMintNewGuestIdentity_whenNoTokenProvided() {
        IssuedGuestToken issued = new IssuedGuestToken("new-raw-token", "new-token-hash");
        when(guestCartTokenIssuer.issue()).thenReturn(issued);
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(null, null);

        assertThat(result.created()).isTrue();
        assertThat(result.newRawGuestToken()).isEqualTo("new-raw-token");
        assertThat(result.cart().getGuestTokenHash()).isEqualTo("new-token-hash");
    }

    @Test
    void shouldMintNewGuestIdentity_whenTokenProvidedButStale() {
        String rawToken = "stale-raw-token";
        String hash = GuestTokenHasher.sha256Hex(rawToken);
        when(cartRepository.findActiveByGuestTokenHash(hash)).thenReturn(Optional.empty());
        IssuedGuestToken issued = new IssuedGuestToken("new-raw-token", "new-token-hash");
        when(guestCartTokenIssuer.issue()).thenReturn(issued);
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(null, rawToken);

        assertThat(result.created()).isTrue();
        assertThat(result.newRawGuestToken()).isEqualTo("new-raw-token");
    }

    @Test
    void shouldSaveNewlyMintedGuestCart_toRepository() {
        IssuedGuestToken issued = new IssuedGuestToken("new-raw-token", "new-token-hash");
        when(guestCartTokenIssuer.issue()).thenReturn(issued);
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(null, null);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getGuestTokenHash()).isEqualTo("new-token-hash");
    }
}

package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.application.usecase.AddItemUseCase.AddItemCommand;
import com.ecommerce.cart.application.usecase.AddItemUseCase.AddItemResult;
import com.ecommerce.cart.application.usecase.GetOrCreateCartUseCase.GetOrCreateCartResult;
import com.ecommerce.cart.domain.exception.ProductNotAvailableException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort.CatalogProductView;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddItemUseCase}.
 *
 * <p>Fakes/mocks at ports and internal collaborators: {@link CartRepository},
 * {@link ProductCatalogPort}, {@link GetOrCreateCartUseCase}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AddItemUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-31T12:00:00Z");

    @Mock
    private GetOrCreateCartUseCase getOrCreateCartUseCase;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductCatalogPort productCatalogPort;

    private AddItemUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        useCase = new AddItemUseCase(getOrCreateCartUseCase, cartRepository, productCatalogPort, clock);
    }

    private CatalogProductView activeProduct() {
        return new CatalogProductView(productId, "Widget", new Money(new BigDecimal("19.99"), "USD"));
    }

    // ── successful add ────────────────────────────────────────────────────

    @Test
    void shouldAddItem_whenProductIsActive() {
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.of(activeProduct()));
        Cart cart = Cart.createCustomerCart(customerId);
        when(getOrCreateCartUseCase.execute(customerId, null))
                .thenReturn(new GetOrCreateCartResult(cart, false, null));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        AddItemResult result = useCase.execute(
                new AddItemCommand(customerId, null, productId, Quantity.of(2)));

        assertThat(result.cart().getLines()).hasSize(1);
        assertThat(result.cart().getLines().get(0).productId()).isEqualTo(productId);
        assertThat(result.cart().getLines().get(0).quantity()).isEqualTo(Quantity.of(2));
    }

    @Test
    void shouldStampAddedAt_usingInjectedClock_whenAddingNewLine() {
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.of(activeProduct()));
        Cart cart = Cart.createCustomerCart(customerId);
        when(getOrCreateCartUseCase.execute(customerId, null))
                .thenReturn(new GetOrCreateCartResult(cart, false, null));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        AddItemResult result = useCase.execute(
                new AddItemCommand(customerId, null, productId, Quantity.of(1)));

        assertThat(result.cart().getLines().get(0).addedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void shouldSaveMutatedCart_toRepository() {
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.of(activeProduct()));
        Cart cart = Cart.createCustomerCart(customerId);
        when(getOrCreateCartUseCase.execute(customerId, null))
                .thenReturn(new GetOrCreateCartResult(cart, false, null));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AddItemCommand(customerId, null, productId, Quantity.of(1)));

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getLines()).hasSize(1);
    }

    @Test
    void shouldPropagateNewRawGuestToken_whenGetOrCreateMintedOne() {
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.of(activeProduct()));
        Cart cart = Cart.createGuestCart("hash-abc");
        when(getOrCreateCartUseCase.execute(null, null))
                .thenReturn(new GetOrCreateCartResult(cart, true, "raw-guest-token"));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        AddItemResult result = useCase.execute(new AddItemCommand(null, null, productId, Quantity.of(1)));

        assertThat(result.newRawGuestToken()).isEqualTo("raw-guest-token");
    }

    // ── product not available ────────────────────────────────────────────

    @Test
    void shouldThrowProductNotAvailableException_whenCatalogHasNoActiveProduct() {
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AddItemCommand(customerId, null, productId, Quantity.of(1))))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void shouldNeverResolveCartOrSave_whenProductIsNotAvailable() {
        when(productCatalogPort.findActiveProduct(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AddItemCommand(customerId, null, productId, Quantity.of(1))))
                .isInstanceOf(ProductNotAvailableException.class);

        verify(getOrCreateCartUseCase, never()).execute(any(), any());
        verify(cartRepository, never()).save(any());
    }
}

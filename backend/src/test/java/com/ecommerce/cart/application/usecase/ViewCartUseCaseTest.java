package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.exception.CartNotFoundException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.ProductSnapshot;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewCartUseCase} — including the read-time retired-product flagging path
 * via a mocked {@link ProductCatalogPort}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ViewCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductCatalogPort productCatalogPort;

    private ViewCartUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final ProductId activeProductId = ProductId.generate();
    private final ProductId retiredProductId = ProductId.generate();

    @BeforeEach
    void setUp() {
        useCase = new ViewCartUseCase(cartRepository, productCatalogPort);
    }

    private Cart cartWithTwoLines() {
        Cart cart = Cart.createCustomerCart(customerId);
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        cart.addItem(activeProductId, new ProductSnapshot("Widget", new Money(new BigDecimal("9.99"), "USD")),
                Quantity.of(1), now);
        cart.addItem(retiredProductId, new ProductSnapshot("Gadget", new Money(new BigDecimal("5.00"), "USD")),
                Quantity.of(1), now);
        return cart;
    }

    @Test
    void shouldReturnCart_withNoLinesFlagged_whenAllProductsStillActive() {
        Cart cart = cartWithTwoLines();
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(productCatalogPort.findActiveProduct(activeProductId))
                .thenReturn(Optional.of(new CatalogProductView(activeProductId, "Widget", new Money(new BigDecimal("9.99"), "USD"))));
        when(productCatalogPort.findActiveProduct(retiredProductId))
                .thenReturn(Optional.of(new CatalogProductView(retiredProductId, "Gadget", new Money(new BigDecimal("5.00"), "USD"))));

        Cart result = useCase.execute(customerId, null);

        assertThat(result.getLines()).allSatisfy(line -> assertThat(line.unavailable()).isFalse());
    }

    @Test
    void shouldFlagLineUnavailable_whenProductNoLongerActiveInCatalog() {
        Cart cart = cartWithTwoLines();
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(productCatalogPort.findActiveProduct(activeProductId))
                .thenReturn(Optional.of(new CatalogProductView(activeProductId, "Widget", new Money(new BigDecimal("9.99"), "USD"))));
        when(productCatalogPort.findActiveProduct(retiredProductId)).thenReturn(Optional.empty());

        Cart result = useCase.execute(customerId, null);

        boolean activeLineFlagged = result.getLines().stream()
                .filter(l -> l.productId().equals(activeProductId))
                .findFirst().orElseThrow().unavailable();
        boolean retiredLineFlagged = result.getLines().stream()
                .filter(l -> l.productId().equals(retiredProductId))
                .findFirst().orElseThrow().unavailable();

        assertThat(activeLineFlagged).isFalse();
        assertThat(retiredLineFlagged).isTrue();
    }

    @Test
    void shouldThrowCartNotFoundException_whenNoActiveCart() {
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(customerId, null))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void shouldNeverPersist_theFlaggedFlagIsComputedOnReadOnly() {
        Cart cart = cartWithTwoLines();
        when(cartRepository.findActiveByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(productCatalogPort.findActiveProduct(activeProductId)).thenReturn(Optional.empty());
        when(productCatalogPort.findActiveProduct(retiredProductId)).thenReturn(Optional.empty());

        useCase.execute(customerId, null);

        verify(cartRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

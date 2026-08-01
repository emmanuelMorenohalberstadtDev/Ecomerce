package com.ecommerce.cart.application.usecase;

import com.ecommerce.cart.domain.exception.ProductNotAvailableException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.ProductSnapshot;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort;
import com.ecommerce.cart.domain.port.out.ProductCatalogPort.CatalogProductView;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * Adds a product to the caller's cart, auto-creating the cart (and, for a first-time guest,
 * minting a new guest identity) if none exists yet — "add to cart" always works, with no
 * separate "create my cart" step required by the client.
 *
 * <p>Validates the product against catalog ({@link ProductCatalogPort}) before copying its
 * {@link ProductSnapshot} — rejects nonexistent or RETIRED products
 * ({@link ProductNotAvailableException}, 422). The snapshot is never re-derived later; this is
 * the one and only point where cart reads catalog for this line's display data (until the next
 * explicit add of the same product).
 *
 * <p>Auth decision table row: GUEST — 200 (own cart, own or new token); CUSTOMER — 200 (own
 * cart); ADMIN — 403 ({@link PreAuthorize}).
 *
 * <p>Transaction boundary is this use case class; {@link GetOrCreateCartUseCase} is invoked as
 * an internal collaborator and joins the same transaction (default {@code REQUIRED} propagation).
 */
@Service
@Transactional
public class AddItemUseCase {

    private final GetOrCreateCartUseCase getOrCreateCartUseCase;
    private final CartRepository cartRepository;
    private final ProductCatalogPort productCatalogPort;
    private final Clock clock;

    public AddItemUseCase(GetOrCreateCartUseCase getOrCreateCartUseCase, CartRepository cartRepository,
                          ProductCatalogPort productCatalogPort, Clock clock) {
        this.getOrCreateCartUseCase = Objects.requireNonNull(getOrCreateCartUseCase);
        this.cartRepository = Objects.requireNonNull(cartRepository);
        this.productCatalogPort = Objects.requireNonNull(productCatalogPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("!hasRole('ADMIN')")
    public AddItemResult execute(AddItemCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        CatalogProductView product = productCatalogPort.findActiveProduct(command.productId())
                .orElseThrow(() -> new ProductNotAvailableException(
                        "Product not available: " + command.productId()));

        GetOrCreateCartUseCase.GetOrCreateCartResult resolved =
                getOrCreateCartUseCase.execute(command.customerId(), command.rawGuestToken());

        Cart cart = resolved.cart();
        ProductSnapshot snapshot = new ProductSnapshot(product.name(), product.price());
        cart.addItem(command.productId(), snapshot, command.quantity(), clock.instant());
        Cart saved = cartRepository.save(cart);

        return new AddItemResult(saved, resolved.newRawGuestToken());
    }

    public record AddItemCommand(CustomerId customerId, String rawGuestToken, ProductId productId, Quantity quantity) {}

    /** @param newRawGuestToken non-null only when a brand-new guest identity was minted for this call */
    public record AddItemResult(Cart cart, String newRawGuestToken) {}
}

package com.ecommerce.cart.infrastructure.persistence;

import com.ecommerce.cart.domain.exception.CartConcurrentModificationException;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.domain.model.CartLine;
import com.ecommerce.cart.domain.model.CartStatus;
import com.ecommerce.cart.domain.model.ProductSnapshot;
import com.ecommerce.cart.domain.port.out.CartRepository;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link CartRepository} port.
 *
 * <p>Maps between {@link Cart} (domain, including its {@link CartLine} children) and
 * {@link CartJpaEntity} (JPA, including {@link CartItemJpaEntity} children), mirroring
 * {@code JpaProductRepository} exactly: {@link #save} always builds a brand-new detached entity
 * from current domain state and hands it to {@code SpringDataCartDao.save(...)}, which — thanks
 * to {@link CartJpaEntity#isNew()} always returning {@code false} — always resolves to
 * {@code entityManager.merge(...)}, correctly performing an INSERT or UPDATE either way.
 *
 * <p><strong>Optimistic lock is enforced via an explicit conditional bump, not {@code @Version}
 * dirty-checking:</strong> {@code items} is a bidirectional {@code @OneToMany(mappedBy = "cart")}
 * — the FK lives on {@code cart_items}, so Hibernate does not increment {@code carts.version} on
 * collection-only changes (the association's non-owning side is invisible to the parent's dirty
 * check), meaning two editors racing to add different items would otherwise both succeed, the
 * second silently discarding the first's line via merge's collection reconciliation. {@link #save}
 * therefore always issues {@code SpringDataCartDao.bumpVersionIfMatches} first — an atomic
 * {@code UPDATE ... WHERE version = :expected} guard, mirroring
 * {@code inventory.infrastructure.persistence.SpringDataStockItemDao#decrementIfSufficientStock} —
 * before merging the rest of the entity graph. Zero rows affected means a concurrent editor won;
 * translated to {@link CartConcurrentModificationException} (409), the adapter's job per the
 * hexagonal skill ("adapters translate errors: vendor exceptions become domain results/exceptions
 * at the boundary").
 */
public class JpaCartRepository implements CartRepository {

    private final SpringDataCartDao springDataCartDao;
    private final Clock clock;

    public JpaCartRepository(SpringDataCartDao springDataCartDao, Clock clock) {
        this.springDataCartDao = Objects.requireNonNull(springDataCartDao);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Cart save(Cart cart) {
        long nextVersion = cart.getVersion();
        if (springDataCartDao.existsById(cart.getId().value())) {
            int updated = springDataCartDao.bumpVersionIfMatches(cart.getId().value(), cart.getVersion());
            if (updated == 0) {
                throw new CartConcurrentModificationException(
                        "Cart " + cart.getId() + " was modified concurrently; reload and retry", null);
            }
            nextVersion = cart.getVersion() + 1;
        }
        CartJpaEntity entity = toEntity(cart, nextVersion);
        CartJpaEntity saved = springDataCartDao.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Cart> findById(CartId id) {
        return springDataCartDao.findByIdFetchItems(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Cart> findActiveByCustomerId(CustomerId customerId) {
        return springDataCartDao.findActiveByCustomerIdFetchItems(customerId.value()).map(this::toDomain);
    }

    @Override
    public Optional<Cart> findActiveByGuestTokenHash(String guestTokenHash) {
        return springDataCartDao.findActiveByGuestTokenHashFetchItems(guestTokenHash).map(this::toDomain);
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private CartJpaEntity toEntity(Cart cart, long version) {
        Instant now = clock.instant();
        CartJpaEntity entity = new CartJpaEntity(
                cart.getId().value(),
                cart.getCustomerId() == null ? null : cart.getCustomerId().value(),
                cart.getGuestTokenHash(),
                cart.getStatus().name(),
                version,
                now // createdAt: ignored on UPDATE because updatable=false
        );
        List<CartItemJpaEntity> items = cart.getLines().stream()
                .map(line -> new CartItemJpaEntity(
                        entity,
                        line.productId().value(),
                        line.snapshot().name(),
                        line.snapshot().unitPrice().amount(),
                        line.snapshot().unitPrice().currency(),
                        line.quantity().value(),
                        line.unavailable(),
                        line.addedAt()))
                .toList();
        entity.replaceItems(items);
        return entity;
    }

    private Cart toDomain(CartJpaEntity entity) {
        List<CartLine> lines = entity.getItems().stream()
                .map(i -> new CartLine(
                        ProductId.of(i.getProductId()),
                        new ProductSnapshot(i.getProductName(), new Money(i.getUnitPrice(), i.getCurrency())),
                        Quantity.of(i.getQuantity()),
                        i.isUnavailable(),
                        i.getCreatedAt()))
                .toList();
        return Cart.reconstitute(
                CartId.of(entity.getId()),
                entity.getCustomerId() == null ? null : CustomerId.of(entity.getCustomerId()),
                entity.getGuestTokenHash(),
                CartStatus.valueOf(entity.getStatus()),
                entity.getVersion(),
                lines);
    }
}

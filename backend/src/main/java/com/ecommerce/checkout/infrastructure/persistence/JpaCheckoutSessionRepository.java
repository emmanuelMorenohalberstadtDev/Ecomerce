package com.ecommerce.checkout.infrastructure.persistence;

import com.ecommerce.checkout.domain.exception.CheckoutSessionConcurrentModificationException;
import com.ecommerce.checkout.domain.exception.DuplicateIdempotencyKeyException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.model.SessionStatus;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ReservationId;
import com.ecommerce.shared.money.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA adapter implementing the domain's {@link CheckoutSessionRepository} port.
 *
 * <p>Maps between {@link CheckoutSession} (domain) and {@link CheckoutSessionJpaEntity} (JPA).
 * {@link #create}/{@link #save} both build a brand-new detached entity from current domain state
 * and hand it to {@code SpringDataCheckoutSessionDao.save(...)}, which — thanks to
 * {@link CheckoutSessionJpaEntity#isNew()} always returning {@code false} — always resolves to
 * {@code entityManager.merge(...)}, correctly performing an INSERT or UPDATE either way. Mirrors
 * {@code JpaCartRepository} exactly.
 *
 * <p>Translates vendor exceptions into checkout's own domain exceptions at the boundary
 * (hexagonal skill: "adapters translate errors"): a lost optimistic-lock race
 * ({@link ObjectOptimisticLockingFailureException}) becomes
 * {@link CheckoutSessionConcurrentModificationException} (409); a lost idempotency-key race on
 * {@code create} ({@link DataIntegrityViolationException} against
 * {@code uq_checkout_sessions_idempotency} — unchanged constraint name, now scoped
 * {@code (customer_id, idempotency_key)} instead of globally, api-guidelines §5.5) becomes
 * {@link DuplicateIdempotencyKeyException} (409) — the backstop for the check-then-act gap in
 * {@code StartCheckoutUseCase} between its own up-front lookup and this insert.
 *
 * <p>Per {@link CheckoutSessionJpaEntity}'s totals-only mapping (no per-line child table), every
 * reconstitution builds a {@link RecalculatedTotal} with an empty {@code lines} list — the
 * per-line breakdown never survives a round-trip through persistence, only the aggregate totals
 * do.
 */
public class JpaCheckoutSessionRepository implements CheckoutSessionRepository {

    private final SpringDataCheckoutSessionDao springDataCheckoutSessionDao;

    public JpaCheckoutSessionRepository(SpringDataCheckoutSessionDao springDataCheckoutSessionDao) {
        this.springDataCheckoutSessionDao = Objects.requireNonNull(springDataCheckoutSessionDao);
    }

    @Override
    public CheckoutSession create(CheckoutSession session) {
        try {
            return saveInternal(session);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateIdempotencyKeyException(
                    "Idempotency-Key has already been used to start a checkout session: "
                            + session.getIdempotencyKey());
        }
    }

    @Override
    public CheckoutSession save(CheckoutSession session) {
        try {
            return saveInternal(session);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CheckoutSessionConcurrentModificationException(
                    "CheckoutSession " + session.getId() + " was modified concurrently; reload and retry", e);
        }
    }

    @Override
    public Optional<CheckoutSession> findById(CheckoutSessionId id) {
        return springDataCheckoutSessionDao.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<CheckoutSession> findByIdAndCustomerId(CheckoutSessionId id, CustomerId customerId) {
        return springDataCheckoutSessionDao.findByIdAndCustomerId(id.value(), customerId.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<CheckoutSession> findByCustomerIdAndIdempotencyKey(CustomerId customerId, String idempotencyKey) {
        return springDataCheckoutSessionDao
                .findByCustomerIdAndIdempotencyKey(customerId.value(), idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public Optional<CheckoutSession> findOpenByCustomerId(CustomerId customerId) {
        return springDataCheckoutSessionDao.findOpenByCustomerId(customerId.value()).stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public List<CheckoutSession> findAwaitingPaymentExpiredAsOf(Instant now) {
        return springDataCheckoutSessionDao.findAwaitingPaymentExpiredAsOf(now).stream()
                .map(this::toDomain)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private CheckoutSession saveInternal(CheckoutSession session) {
        CheckoutSessionJpaEntity entity = toEntity(session);
        CheckoutSessionJpaEntity saved = springDataCheckoutSessionDao.save(entity);
        return toDomain(saved);
    }

    private CheckoutSessionJpaEntity toEntity(CheckoutSession session) {
        RecalculatedTotal total = session.getRecalculatedTotal();
        return new CheckoutSessionJpaEntity(
                session.getId().value(),
                session.getCustomerId().value(),
                session.getCartId().value(),
                session.getStatus().name(),
                session.getReservationId() == null ? null : session.getReservationId().value(),
                total.subtotal().amount(),
                total.discountTotal().amount(),
                total.shippingFee().amount(),
                total.grandTotal().amount(),
                session.getExpectedTotal().amount(),
                total.grandTotal().currency(),
                session.getPaymentDeadline(),
                session.getIdempotencyKey(),
                session.getVersion(),
                session.getCreatedAt());
    }

    private CheckoutSession toDomain(CheckoutSessionJpaEntity entity) {
        String currency = entity.getCurrency();
        RecalculatedTotal total = new RecalculatedTotal(
                List.of(),
                new Money(entity.getSubtotal(), currency),
                new Money(entity.getDiscountTotal(), currency),
                new Money(entity.getShippingFee(), currency),
                new Money(entity.getGrandTotal(), currency));
        return CheckoutSession.reconstitute(
                CheckoutSessionId.of(entity.getId()),
                CustomerId.of(entity.getCustomerId()),
                CartId.of(entity.getCartId()),
                SessionStatus.valueOf(entity.getStatus()),
                entity.getReservationId() == null ? null : ReservationId.of(entity.getReservationId()),
                total,
                new Money(entity.getExpectedTotal(), currency),
                entity.getPaymentDeadline(),
                entity.getIdempotencyKey(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

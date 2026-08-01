package com.ecommerce.cart.domain.model;

import com.ecommerce.cart.domain.exception.CartLineNotFoundException;
import com.ecommerce.cart.domain.exception.CartNotActiveException;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.quantity.Quantity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for the cart bounded context (domain-model.md §2/§3).
 *
 * <p>Owns shopping intent for a guest (identified by a hashed anonymous cart token) or a
 * registered customer — never both, never neither, mirroring {@code ck_carts_identity} in
 * {@code V003__create_cart_schema.sql}. Owns its {@link CartLine} children (one per product) and
 * the merge-on-login policy (rule 1, see {@link #mergeFrom}).
 *
 * <p><strong>Guest identity, hashed only:</strong> {@link #guestTokenHash} is always the SHA-256
 * hash of the opaque guest cart token, never the raw value (security-architecture §2.7). This
 * class never constructs or compares a raw token — hashing happens at the presentation/
 * infrastructure boundary ({@code cart.infrastructure.security.GuestCartTokenGenerator},
 * {@code com.ecommerce.common.security.TokenHashing}); the domain only ever sees the hash as an
 * opaque {@code String} identifier, exactly like a password hash.
 *
 * <p><strong>Per-line quantity cap:</strong> {@link #MAX_QUANTITY_PER_LINE} is an
 * <em>assumption</em> — no numeric value is documented anywhere in the repository
 * (domain-model.md only says "per-line cap aware" / "capped per line" without a number). Flagged
 * here pending product-owner confirmation; see the cart handoff report.
 *
 * <p>Created via {@link #createGuestCart}/{@link #createCustomerCart}; reconstituted from
 * persistence via {@link #reconstitute}. No public setters — state changes happen through named
 * domain methods.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public class Cart {

    /**
     * Maximum quantity a single {@link CartLine} may ever hold, whether reached by a direct
     * {@link #addItem}/{@link #updateLineQuantity} call or by summation in {@link #mergeFrom}.
     *
     * <p><strong>Assumption, pending PO confirmation:</strong> no cap value is documented in
     * domain-model.md, database-design.md, or api-guidelines.md — only that a cap exists. 99 is
     * chosen as a conventional ecommerce default (matches the max a 2-digit quantity stepper
     * UI typically allows) and is not derived from any written business rule.
     */
    public static final int MAX_QUANTITY_PER_LINE = 99;

    private final CartId id;
    private CustomerId customerId;
    private String guestTokenHash;
    private CartStatus status;
    private long version;
    private final List<CartLine> lines;

    private Cart(CartId id, CustomerId customerId, String guestTokenHash, CartStatus status,
                long version, List<CartLine> lines) {
        if (id == null) {
            throw new IllegalArgumentException("CartId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("CartStatus must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        boolean hasCustomer = customerId != null;
        boolean hasGuestToken = guestTokenHash != null && !guestTokenHash.isBlank();
        if (hasCustomer == hasGuestToken) {
            // XOR violated: both present or both absent (ck_carts_identity in V003).
            throw new IllegalArgumentException(
                    "Cart must have exactly one of customerId or guestTokenHash, got customerId="
                            + customerId + ", guestTokenHash="
                            + (guestTokenHash == null ? "null" : "[present]"));
        }

        this.id = id;
        this.customerId = customerId;
        this.guestTokenHash = guestTokenHash;
        this.status = status;
        this.version = version;
        this.lines = new ArrayList<>(lines == null ? List.of() : lines);
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /** Creates a brand-new, empty ACTIVE cart for an anonymous guest identified by {@code guestTokenHash}. */
    public static Cart createGuestCart(String guestTokenHash) {
        if (guestTokenHash == null || guestTokenHash.isBlank()) {
            throw new IllegalArgumentException("guestTokenHash must not be null or blank");
        }
        return new Cart(CartId.generate(), null, guestTokenHash, CartStatus.ACTIVE, 0L, new ArrayList<>());
    }

    /** Creates a brand-new, empty ACTIVE cart owned by {@code customerId}. */
    public static Cart createCustomerCart(CustomerId customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }
        return new Cart(CartId.generate(), customerId, null, CartStatus.ACTIVE, 0L, new ArrayList<>());
    }

    /** Factory for reconstitution from persistence. Restores exact state without re-running creation rules. */
    public static Cart reconstitute(CartId id, CustomerId customerId, String guestTokenHash,
                                    CartStatus status, long version, List<CartLine> lines) {
        return new Cart(id, customerId, guestTokenHash, status, version, lines);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Adds {@code quantity} of the product described by {@code snapshot} to this cart.
     *
     * <p>Upsert semantics: if this product already has a line, the quantities are summed (capped
     * at {@link #MAX_QUANTITY_PER_LINE}) and the line's snapshot is refreshed to the one passed
     * here (this call just re-validated the product against catalog, so refreshing is a
     * deliberate choice — see class javadoc; it does not contradict the "never re-derive on
     * read" rule, which is about reads, not fresh add actions). Otherwise a new line is created
     * with {@code now} as its {@code addedAt}.
     *
     * @throws CartNotActiveException if this cart is not ACTIVE
     */
    public void addItem(ProductId productId, ProductSnapshot snapshot, Quantity quantity, Instant now) {
        requireActive();
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Optional<CartLine> existing = findLine(productId);
        if (existing.isPresent()) {
            CartLine old = existing.get();
            int combined = old.quantity().value() + quantity.value();
            replaceLine(new CartLine(productId, snapshot, capQuantity(combined), old.unavailable(), old.addedAt()));
        } else {
            lines.add(new CartLine(productId, snapshot, capQuantity(quantity.value()), false, now));
        }
    }

    /**
     * Replaces the quantity of an existing line. Snapshot, unavailability flag, and
     * {@code addedAt} are preserved.
     *
     * @throws CartNotActiveException if this cart is not ACTIVE
     * @throws CartLineNotFoundException if {@code productId} is not a line in this cart
     */
    public void updateLineQuantity(ProductId productId, Quantity quantity) {
        requireActive();
        Objects.requireNonNull(quantity, "quantity must not be null");
        CartLine old = requireLine(productId);
        replaceLine(new CartLine(productId, old.snapshot(), capQuantity(quantity.value()), old.unavailable(), old.addedAt()));
    }

    /**
     * Removes a line entirely.
     *
     * @throws CartNotActiveException if this cart is not ACTIVE
     * @throws CartLineNotFoundException if {@code productId} is not a line in this cart
     */
    public void removeLine(ProductId productId) {
        requireActive();
        requireLine(productId);
        lines.removeIf(l -> l.productId().equals(productId));
    }

    /**
     * Flags a line as unavailable (its product is no longer ACTIVE in catalog, rule 10). A
     * no-op if {@code productId} is not a line in this cart (a line may have been removed by the
     * time a caller re-checks it — not an error).
     *
     * <p>Deliberately has no {@code requireActive()} guard: flagging is applied read-time by
     * {@code ViewCartUseCase} regardless of status, and does not need to be persisted (see that
     * use case's javadoc for the "compute on read, don't persist" choice, matching the
     * {@code cart_items.unavailable} column comment in V003: "never drives a CHECK ... never
     * re-validated at checkout regardless of this flag's freshness").
     */
    public void markLineUnavailable(ProductId productId) {
        findLine(productId).ifPresent(old ->
                replaceLine(new CartLine(productId, old.snapshot(), old.quantity(), true, old.addedAt())));
    }

    /**
     * Merges {@code guestCart}'s lines into this (customer) cart, then transitions
     * {@code guestCart} to {@link CartStatus#MERGED} and clears its guest token hash
     * (security-architecture §2.7: "the guest token is invalidated at merge — it never resolves
     * to the customer's cart").
     *
     * <p><strong>Policy implemented (domain-model.md §1/§5, PO rule 1) — quoted verbatim:</strong>
     * "same product → quantities summed and capped per line; conflicting carts → freshest wins."
     * Neither term is defined more precisely anywhere else in the repository (checked
     * domain-model.md, database-design.md, api-guidelines.md, security-architecture.md — none go
     * further). This method's explicit interpretation, stated here as an assumption rather than
     * invented silently:
     * <ul>
     *   <li><strong>"Conflicting carts"</strong> = both carts have unresolved state to reconcile —
     *       i.e. this whole method IS the conflict-resolution path; there is no separate
     *       "conflict detection" step beyond per-line merging.</li>
     *   <li><strong>"Freshest wins"</strong> = applies only within a per-line merge, and only in
     *       the edge case where the two carts hold different {@link ProductSnapshot}s for what
     *       should be the same product (e.g. a stale guest-side snapshot vs. a newer customer-side
     *       one, or vice versa) — an edge case that should not arise from normal quantity summation
     *       alone. When it does, the snapshot (and {@code addedAt}) from whichever line has the
     *       more recent {@code addedAt} is kept; the other is discarded. No further tie-break
     *       machinery is implemented beyond what this text justifies.</li>
     * </ul>
     *
     * @throws CartNotActiveException if either this cart or {@code guestCart} is not ACTIVE
     */
    public void mergeFrom(Cart guestCart) {
        Objects.requireNonNull(guestCart, "guestCart must not be null");
        requireActive();
        if (guestCart.status != CartStatus.ACTIVE) {
            throw new CartNotActiveException(
                    "Guest cart " + guestCart.id + " is not ACTIVE (status=" + guestCart.status
                            + ") and cannot be merged");
        }

        for (CartLine guestLine : guestCart.lines) {
            Optional<CartLine> existing = findLine(guestLine.productId());
            if (existing.isPresent()) {
                CartLine mine = existing.get();
                int combined = mine.quantity().value() + guestLine.quantity().value();

                ProductSnapshot snapshot = mine.snapshot();
                Instant addedAt = mine.addedAt();
                if (!mine.snapshot().equals(guestLine.snapshot()) && guestLine.addedAt().isAfter(mine.addedAt())) {
                    snapshot = guestLine.snapshot();
                    addedAt = guestLine.addedAt();
                }
                replaceLine(new CartLine(mine.productId(), snapshot, capQuantity(combined), false, addedAt));
            } else {
                lines.add(new CartLine(guestLine.productId(), guestLine.snapshot(),
                        capQuantity(guestLine.quantity().value()), guestLine.unavailable(), guestLine.addedAt()));
            }
        }

        guestCart.markMerged();
    }

    /** Transitions this cart to {@link CartStatus#MERGED} and clears its guest token hash. Called only by {@link #mergeFrom} on the guest side. */
    public void markMerged() {
        this.status = CartStatus.MERGED;
        this.guestTokenHash = null;
    }

    /**
     * Transfers ownership of this (guest) cart directly to {@code customerId} — used when the
     * customer has no existing ACTIVE cart to merge into, so there is nothing to merge (see
     * {@code MergeCartsUseCase}).
     *
     * @throws IllegalStateException if this cart already belongs to a customer
     * @throws CartNotActiveException if this cart is not ACTIVE
     */
    public void claimForCustomer(CustomerId customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        requireActive();
        if (this.customerId != null) {
            throw new IllegalStateException("Cart " + id + " already belongs to a customer");
        }
        this.customerId = customerId;
        this.guestTokenHash = null;
    }

    // -------------------------------------------------------------------------
    // Getters — no public setters; state changes via domain methods only
    // -------------------------------------------------------------------------

    public CartId getId() { return id; }

    public CustomerId getCustomerId() { return customerId; }

    public String getGuestTokenHash() { return guestTokenHash; }

    public CartStatus getStatus() { return status; }

    public long getVersion() { return version; }

    /** Unmodifiable, defensively-copied view of this cart's lines. */
    public List<CartLine> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireActive() {
        if (status != CartStatus.ACTIVE) {
            throw new CartNotActiveException("Cart " + id + " is not ACTIVE (status=" + status + ")");
        }
    }

    private Optional<CartLine> findLine(ProductId productId) {
        return lines.stream().filter(l -> l.productId().equals(productId)).findFirst();
    }

    private CartLine requireLine(ProductId productId) {
        return findLine(productId).orElseThrow(() -> new CartLineNotFoundException(
                "Cart " + id + " has no line for product " + productId));
    }

    private void replaceLine(CartLine line) {
        lines.removeIf(l -> l.productId().equals(line.productId()));
        lines.add(line);
    }

    private static Quantity capQuantity(int rawValue) {
        return Quantity.of(Math.min(rawValue, MAX_QUANTITY_PER_LINE));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cart cart)) return false;
        return Objects.equals(id, cart.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

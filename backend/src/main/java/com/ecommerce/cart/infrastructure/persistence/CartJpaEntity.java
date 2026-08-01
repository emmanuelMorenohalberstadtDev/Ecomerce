package com.ecommerce.cart.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping the {@code carts} table.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code id} has no {@code @GeneratedValue} — the application generates UUIDv7 before
 *       INSERT (ADR-0002), matching {@code ProductJpaEntity}'s convention.</li>
 *   <li>{@code customerId}/{@code guestTokenHash} are plain nullable columns; the XOR invariant
 *       ({@code ck_carts_identity}) is enforced in the domain ({@code Cart}'s constructor) and
 *       backstopped at the DB.</li>
 *   <li>{@code status} is a plain {@code String}, matching {@code ProductJpaEntity.status}.</li>
 *   <li>{@code @Version} maps {@code carts.version} for optimistic locking
 *       (database-design.md §7).</li>
 *   <li><strong>{@code implements Persistable<UUID>}, {@code isNew()} always {@code false}:</strong>
 *       this entity has both a manually-assigned (never-null) id AND a {@code @Version} field.
 *       Spring Data JPA's default new-entity detection prefers the version property when one
 *       exists (new ⇔ version is the primitive default, {@code 0L}) — which would misclassify a
 *       cart being updated for the very first time (version still {@code 0} after its initial
 *       insert) as "new" and re-attempt an {@code INSERT}, colliding on the primary key. Forcing
 *       {@code isNew() == false} makes {@code SimpleJpaRepository.save()} always call
 *       {@code entityManager.merge(...)}, exactly like {@code ProductJpaEntity} already relies on
 *       implicitly (no {@code @Version} there, so the id-based default already always resolves to
 *       {@code merge()}) — {@code merge()} correctly performs an INSERT or UPDATE based on
 *       whether the row exists, independent of this flag, and still honours {@code @Version} for
 *       optimistic-lock checking on the UPDATE path.</li>
 *   <li>{@code items} is {@code FetchType.LAZY} (database-design.md §7: "lazy everything") —
 *       {@code SpringDataCartDao} uses {@code JOIN FETCH} on every finder so lines are always
 *       loaded in the same query as the cart, per "fetch per use case with join fetch" in the
 *       same section.</li>
 * </ul>
 *
 * <p>This class lives in {@code infrastructure.persistence} only — satisfies ArchUnit rule
 * {@code jpa_entities_only_in_infrastructure}.
 */
@Entity
@Table(name = "carts")
public class CartJpaEntity implements Persistable<UUID> {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "guest_token_hash")
    private String guestTokenHash;

    @Column(nullable = false)
    private String status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<CartItemJpaEntity> items = new ArrayList<>();

    CartJpaEntity() {}

    public CartJpaEntity(UUID id, UUID customerId, String guestTokenHash, String status,
                         long version, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.guestTokenHash = guestTokenHash;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getGuestTokenHash() { return guestTokenHash; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CartItemJpaEntity> getItems() { return items; }

    /** Replaces the entire item list in place (clear + rebuild), mirroring {@code ProductJpaEntity#replaceImages}. */
    public void replaceItems(List<CartItemJpaEntity> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
    }

    /** Always {@code false} — see class javadoc; forces Spring Data JPA to always use {@code merge()}. */
    @Override
    public boolean isNew() {
        return false;
    }
}

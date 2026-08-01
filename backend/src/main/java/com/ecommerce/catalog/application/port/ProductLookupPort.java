package com.ecommerce.catalog.application.port;

import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;

import java.util.Optional;

/**
 * Outbound-facing, read-only public façade catalog exposes to other bounded contexts that need
 * to know about a product without depending on catalog's internal domain model.
 *
 * <p><strong>Why this exists:</strong> the cart context needs two catalog reads — (1) validate a
 * product exists and is ACTIVE before copying its {@code ProductSnapshot} at add-to-cart time,
 * and (2) at view-cart time, check whether each line's product is still ACTIVE (rule 10, flag
 * retired lines). Catalog's domain layer ({@code Product}, {@code ProductRepository}) is private
 * to catalog's own use cases — a context's {@code application.port} package is its public
 * façade (mirrors {@code AuditLogPort}, the first port to live here for the same reason: it is
 * not a catalog domain repository, but a boundary catalog exposes deliberately). Living here
 * lets {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events} pass in both
 * letter and spirit — {@code .application.port} is not in that rule's forbidden-target list, and
 * unlike {@code .domain.port} it is the *intended* crossing point, not an unguarded gap.
 *
 * <p><strong>ACTIVE-only, by design:</strong> {@link #findActiveById} returns {@link Optional#empty()}
 * for a RETIRED (or nonexistent) product, exactly mirroring {@code GetProductUseCase}'s existing
 * convention that "RETIRED is indistinguishable from nonexistent on the public surface". This
 * lets a single method serve both of cart's read needs: at add-time, empty ⇒ reject the add
 * (see {@code ProductNotAvailableException} in the cart context); at view-time, empty ⇒ the
 * previously-active product has since become unavailable, flag the line.
 *
 * <p>Returns {@link ProductSummary} — a small, read-only projection (id, name, price) — never
 * the full {@code Product} aggregate, so catalog's internal domain shape is free to change
 * without breaking callers across the context boundary.
 */
public interface ProductLookupPort {

    /**
     * Looks up a product by id, but only if it is currently ACTIVE.
     *
     * @param id the product id
     * @return the product's public summary if it exists and is ACTIVE; {@link Optional#empty()}
     *         otherwise (nonexistent or RETIRED — both look identical to the caller, by design)
     */
    Optional<ProductSummary> findActiveById(ProductId id);

    /** Minimal, read-only projection of a product for cross-context consumers. */
    record ProductSummary(ProductId id, String name, Money price) {

        public ProductSummary {
            if (id == null) {
                throw new IllegalArgumentException("ProductSummary id must not be null");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("ProductSummary name must not be null or blank");
            }
            if (price == null) {
                throw new IllegalArgumentException("ProductSummary price must not be null");
            }
        }
    }
}

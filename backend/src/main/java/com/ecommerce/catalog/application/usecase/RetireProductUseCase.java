package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.event.ProductRetiredEvent;
import com.ecommerce.catalog.domain.exception.ProductAlreadyRetiredException;
import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.id.ProductId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Retires a product: {@code ACTIVE -> RETIRED}. Admin-only mutation. Never deletes the row
 * (domain-model.md §1 catalog, rule 10) — see {@link Product#retire()} javadoc for why
 * re-retirement is rejected ({@link ProductAlreadyRetiredException}, 409) rather than treated as
 * an idempotent no-op.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx (route layer +
 * {@link PreAuthorize} defense-in-depth, security-architecture §3.2).
 *
 * <p>Raises {@link ProductRetiredEvent} after persistence and writes one
 * {@code PRODUCT_RETIRED} admin-audit row in the same transaction (security §6c requirement 3).
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class RetireProductUseCase {

    private final ProductRepository productRepository;
    private final AuditLogPort auditLogPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RetireProductUseCase(ProductRepository productRepository,
                                AuditLogPort auditLogPort,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this.productRepository = Objects.requireNonNull(productRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Product execute(RetireProductCommand command) {
        ProductId productId = ProductId.of(command.productId());
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        product.retire();

        Product saved = productRepository.save(product);

        eventPublisher.publishEvent(new ProductRetiredEvent(saved.getId(), clock.instant()));

        auditLogPort.record(CatalogAuditAction.PRODUCT_RETIRED, "Product",
                saved.getId().toString(), Map.of());

        return saved;
    }

    public record RetireProductCommand(String productId) {}
}

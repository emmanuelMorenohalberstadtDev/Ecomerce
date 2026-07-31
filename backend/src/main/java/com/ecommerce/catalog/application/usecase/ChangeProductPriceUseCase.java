package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.domain.event.ProductPriceChangedEvent;
import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Changes a product's current base (list) price. Admin-only mutation.
 *
 * <p>Auth decision table row: GUEST — (401); CUSTOMER — (403); ADMIN — 2xx (route layer +
 * {@link PreAuthorize} defense-in-depth, security-architecture §3.2).
 *
 * <p>Raises {@link ProductPriceChangedEvent} after the new price is persisted — built here
 * (not inside {@link Product}) from the price read off the aggregate before and after
 * {@link Product#changePrice}, mirroring how {@code RegisterUserUseCase} builds
 * {@code UserRegisteredEvent} from post-creation aggregate state (domain-model.md §4: events
 * have no v1 subscribers, kept for the audit trail / future use). Also writes one
 * {@code PRODUCT_PRICE_CHANGED} admin-audit row in the same transaction (security §6c
 * requirement 3) — checkout recalculates prices from catalog at the point of use regardless, so
 * nothing consumes this event synchronously in v1.
 *
 * <p>Transaction boundary is this use case class.
 */
@Service
@Transactional
public class ChangeProductPriceUseCase {

    private final ProductRepository productRepository;
    private final AuditLogPort auditLogPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ChangeProductPriceUseCase(ProductRepository productRepository,
                                     AuditLogPort auditLogPort,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.productRepository = Objects.requireNonNull(productRepository);
        this.auditLogPort = Objects.requireNonNull(auditLogPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Product execute(ChangeProductPriceCommand command) {
        ProductId productId = ProductId.of(command.productId());
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        Money oldPrice = product.getBasePrice();
        Money newPrice = new Money(command.amount(), command.currency());
        product.changePrice(newPrice);

        Product saved = productRepository.save(product);

        eventPublisher.publishEvent(
                new ProductPriceChangedEvent(saved.getId(), oldPrice, newPrice, clock.instant()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("oldPrice", oldPrice.toString());
        details.put("newPrice", newPrice.toString());
        auditLogPort.record(CatalogAuditAction.PRODUCT_PRICE_CHANGED, "Product",
                saved.getId().toString(), details);

        return saved;
    }

    public record ChangeProductPriceCommand(String productId, BigDecimal amount, String currency) {}
}

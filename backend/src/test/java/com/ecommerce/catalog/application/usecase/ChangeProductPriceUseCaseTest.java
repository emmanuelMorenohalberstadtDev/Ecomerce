package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.usecase.ChangeProductPriceUseCase.ChangeProductPriceCommand;
import com.ecommerce.catalog.domain.event.ProductPriceChangedEvent;
import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangeProductPriceUseCase}.
 *
 * <p>Mocks: {@link ProductRepository}, {@link AuditLogPort}, {@link ApplicationEventPublisher}.
 * No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ChangeProductPriceUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private ChangeProductPriceUseCase useCase;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        useCase = new ChangeProductPriceUseCase(productRepository, auditLogPort, eventPublisher, clock);
        existingProduct = Product.create(CategoryId.generate(), new Sku("abc-123"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "desc", List.of());
    }

    @Test
    void shouldUpdatePrice_whenProductExists() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = useCase.execute(new ChangeProductPriceCommand(
                existingProduct.getId().toString(), new BigDecimal("24.99"), "USD"));

        assertThat(result.getBasePrice()).isEqualTo(new Money(new BigDecimal("24.99"), "USD"));
    }

    @Test
    void shouldPublishProductPriceChangedEvent_withOldAndNewPrice() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new ChangeProductPriceCommand(
                existingProduct.getId().toString(), new BigDecimal("24.99"), "USD"));

        ArgumentCaptor<ProductPriceChangedEvent> captor = ArgumentCaptor.forClass(ProductPriceChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        ProductPriceChangedEvent event = captor.getValue();
        assertThat(event.oldPrice()).isEqualTo(new Money(new BigDecimal("19.99"), "USD"));
        assertThat(event.newPrice()).isEqualTo(new Money(new BigDecimal("24.99"), "USD"));
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldWriteAuditLog_withOldAndNewPrice() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new ChangeProductPriceCommand(
                existingProduct.getId().toString(), new BigDecimal("24.99"), "USD"));

        verify(auditLogPort).record(any(), any(), any(), any());
    }

    @Test
    void shouldThrowProductNotFoundException_whenProductDoesNotExist() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ChangeProductPriceCommand(
                existingProduct.getId().toString(), new BigDecimal("24.99"), "USD")))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

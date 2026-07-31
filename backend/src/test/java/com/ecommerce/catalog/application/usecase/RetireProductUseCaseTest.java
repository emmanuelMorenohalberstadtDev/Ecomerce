package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.usecase.RetireProductUseCase.RetireProductCommand;
import com.ecommerce.catalog.domain.event.ProductRetiredEvent;
import com.ecommerce.catalog.domain.exception.ProductAlreadyRetiredException;
import com.ecommerce.catalog.domain.exception.ProductNotFoundException;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.ProductStatus;
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
 * Unit tests for {@link RetireProductUseCase}.
 *
 * <p>Mocks: {@link ProductRepository}, {@link AuditLogPort}, {@link ApplicationEventPublisher}.
 * No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RetireProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    private RetireProductUseCase useCase;
    private Product activeProduct;

    @BeforeEach
    void setUp() {
        useCase = new RetireProductUseCase(productRepository, auditLogPort, eventPublisher, clock);
        activeProduct = Product.create(CategoryId.generate(), new Sku("abc-123"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "desc", List.of());
    }

    @Test
    void shouldSetStatusToRetired_whenProductIsActive() {
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = useCase.execute(new RetireProductCommand(activeProduct.getId().toString()));

        assertThat(result.getStatus()).isEqualTo(ProductStatus.RETIRED);
    }

    @Test
    void shouldPublishProductRetiredEvent() {
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RetireProductCommand(activeProduct.getId().toString()));

        ArgumentCaptor<ProductRetiredEvent> captor = ArgumentCaptor.forClass(ProductRetiredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo(activeProduct.getId());
        assertThat(captor.getValue().occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldWriteAuditLog_onSuccess() {
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RetireProductCommand(activeProduct.getId().toString()));

        verify(auditLogPort).record(any(), any(), any(), any());
    }

    @Test
    void shouldThrowProductNotFoundException_whenProductDoesNotExist() {
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RetireProductCommand(activeProduct.getId().toString())))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldThrowProductAlreadyRetiredException_whenAlreadyRetired() {
        activeProduct.retire();
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> useCase.execute(new RetireProductCommand(activeProduct.getId().toString())))
                .isInstanceOf(ProductAlreadyRetiredException.class);

        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

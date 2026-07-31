package com.ecommerce.catalog.application.usecase;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GetProductUseCase}.
 *
 * <p>Mocks: {@link ProductRepository}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GetProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    private GetProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProductUseCase(productRepository);
    }

    @Test
    void shouldReturnProduct_whenActiveAndExists() {
        Product product = Product.create(CategoryId.generate(), new Sku("abc-123"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "desc", List.of());
        org.mockito.Mockito.when(productRepository.findById(product.getId()))
                .thenReturn(Optional.of(product));

        Product result = useCase.execute(product.getId().toString());

        assertThat(result).isEqualTo(product);
    }

    @Test
    void shouldThrowProductNotFoundException_whenProductDoesNotExist() {
        var id = com.ecommerce.shared.id.ProductId.generate();
        org.mockito.Mockito.when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id.toString()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldThrowProductNotFoundException_whenProductIsRetired() {
        Product product = Product.create(CategoryId.generate(), new Sku("abc-123"),
                new Money(new BigDecimal("19.99"), "USD"), "Widget", "desc", List.of());
        product.retire();
        org.mockito.Mockito.when(productRepository.findById(product.getId()))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() -> useCase.execute(product.getId().toString()))
                .isInstanceOf(ProductNotFoundException.class);
    }
}

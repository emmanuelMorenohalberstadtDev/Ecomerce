package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.usecase.SearchProductsUseCase.SearchProductsQuery;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.port.out.PageResult;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import com.ecommerce.catalog.domain.port.out.ProductSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SearchProductsUseCase}.
 *
 * <p>Mocks: {@link ProductRepository}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SearchProductsUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    private SearchProductsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SearchProductsUseCase(productRepository);
    }

    private static PageResult<Product> emptyPage(int page, int size) {
        return new PageResult<>(List.of(), page, size, 0, 0);
    }

    @Test
    void shouldDelegateToRepositoryWithDefaults_whenNoFiltersProvided() {
        when(productRepository.searchActive(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage(0, 20));

        useCase.execute(new SearchProductsQuery(null, null, null, 0, 20));

        verify(productRepository).searchActive(isNull(), isNull(), eq(ProductSort.defaultSort()), eq(0), eq(20));
    }

    @Test
    void shouldLowercaseNamePrefix_beforeDelegating() {
        when(productRepository.searchActive(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage(0, 20));

        useCase.execute(new SearchProductsQuery(null, "WiDgEt", null, 0, 20));

        ArgumentCaptor<String> namePrefixCaptor = ArgumentCaptor.forClass(String.class);
        verify(productRepository).searchActive(any(), namePrefixCaptor.capture(), any(), anyInt(), anyInt());
        assertThat(namePrefixCaptor.getValue()).isEqualTo("widget");
    }

    @Test
    void shouldParseCategoryIdFilter_whenProvided() {
        CategoryId categoryId = CategoryId.generate();
        when(productRepository.searchActive(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage(0, 20));

        useCase.execute(new SearchProductsQuery(categoryId.toString(), null, null, 0, 20));

        verify(productRepository).searchActive(eq(categoryId), isNull(), any(), eq(0), eq(20));
    }

    @Test
    void shouldClampSizeAboveHardCap() {
        when(productRepository.searchActive(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage(0, 100));

        useCase.execute(new SearchProductsQuery(null, null, null, 0, 500));

        verify(productRepository).searchActive(any(), any(), any(), eq(0), eq(100));
    }

    @Test
    void shouldThrowIllegalArgumentException_whenPageIsNegative() {
        assertThatThrownBy(() -> useCase.execute(new SearchProductsQuery(null, null, null, -1, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenSizeIsZero() {
        assertThatThrownBy(() -> useCase.execute(new SearchProductsQuery(null, null, null, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

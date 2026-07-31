package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListCategoriesUseCase}.
 *
 * <p>Mocks: {@link CategoryRepository}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ListCategoriesUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    private ListCategoriesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListCategoriesUseCase(categoryRepository);
    }

    @Test
    void shouldDelegateToRepository_withValidatedPageAndSize() {
        PageResult<Category> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        when(categoryRepository.findAll(0, 20)).thenReturn(page);

        PageResult<Category> result = useCase.execute(0, 20);

        assertThat(result).isEqualTo(page);
        verify(categoryRepository).findAll(0, 20);
    }

    @Test
    void shouldClampSizeAboveHardCap() {
        PageResult<Category> page = new PageResult<>(List.of(), 0, 100, 0, 0);
        when(categoryRepository.findAll(0, 100)).thenReturn(page);

        useCase.execute(0, 500);

        verify(categoryRepository).findAll(0, 100);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenPageIsNegative() {
        assertThatThrownBy(() -> useCase.execute(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenSizeIsZero() {
        assertThatThrownBy(() -> useCase.execute(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

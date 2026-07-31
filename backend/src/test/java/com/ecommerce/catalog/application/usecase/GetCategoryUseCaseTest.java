package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetCategoryUseCase}.
 *
 * <p>Mocks: {@link CategoryRepository}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GetCategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    private GetCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCategoryUseCase(categoryRepository);
    }

    @Test
    void shouldReturnCategory_whenItExists() {
        Category category = Category.create("Electronics", null);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        Category result = useCase.execute(category.getId().toString());

        assertThat(result).isEqualTo(category);
    }

    @Test
    void shouldThrowCategoryNotFoundException_whenCategoryDoesNotExist() {
        CategoryId id = CategoryId.generate();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id.toString()))
                .isInstanceOf(CategoryNotFoundException.class);
    }
}
